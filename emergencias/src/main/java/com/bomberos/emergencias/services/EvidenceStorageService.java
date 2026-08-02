package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.EvidenciaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceStorageService {

    @Value("${evidence.storage.provider:local}")
    private String provider;

    @Value("${evidence.storage.local-directory:uploads}")
    private String localDirectory;

    @Value("${pocketbase.url:http://127.0.0.1:8090}")
    private String pocketBaseUrl;

    @Value("${pocketbase.collection:evidencias_archivo}")
    private String collection;

    @Value("${pocketbase.token:}")
    private String token;

    @Value("${pocketbase.superuser-email:}")
    private String pocketBaseSuperuserEmail;

    @Value("${pocketbase.superuser-password:}")
    private String pocketBaseSuperuserPassword;

    private volatile String cachedAuthorization;

    public StoredEvidence store(EvidenciaDto file, String sha256, Long reporteId) throws Exception {
        return "pocketbase".equalsIgnoreCase(provider)
                ? storeInPocketBase(file, sha256, reporteId)
                : storeLocally(file, sha256);
    }

    public byte[] load(String storageProvider, String recordId, String filename, String legacyUrl) throws Exception {
        if ("POCKETBASE".equalsIgnoreCase(storageProvider)) {
            requirePocketBaseConfiguration();
            return pocketBaseClient().get()
                    .uri("/api/files/{collection}/{recordId}/{filename}", collection, recordId, filename)
                    .header(HttpHeaders.AUTHORIZATION, pocketBaseAuthorization())
                    .retrieve()
                    .body(byte[].class);
        }

        String relative = filename != null && !filename.isBlank()
                ? filename
                : legacyUrl == null ? "" : legacyUrl.replace("\\", "/").replaceFirst("^/uploads/", "");
        Path base = Paths.get(localDirectory).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Ruta de evidencia no válida");
        }
        return Files.readAllBytes(target);
    }

    private StoredEvidence storeInPocketBase(EvidenciaDto file, String sha256, Long reporteId) throws Exception {
        requirePocketBaseConfiguration();
        ByteArrayResource resource = new ByteArrayResource(file.bytes()) {
            @Override
            public String getFilename() {
                return safeFilename(file.filename());
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", resource);
        form.add("sha256", sha256);
        form.add("reporteId", String.valueOf(reporteId));
        form.add("mimeType", normalizedContentType(file));
        form.add("tamanoBytes", String.valueOf(file.bytes().length));

        JsonNode record = pocketBaseClient().post()
                .uri("/api/collections/{collection}/records", collection)
                .header(HttpHeaders.AUTHORIZATION, pocketBaseAuthorization())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String recordId = requiredText(record, "id");
        String storedFilename = requiredText(record, "archivo");
        log.info("[POCKETBASE] Evidencia {} almacenada en registro {}", sha256, recordId);
        return new StoredEvidence("POCKETBASE", recordId, storedFilename);
    }

    private StoredEvidence storeLocally(EvidenciaDto file, String sha256) throws Exception {
        Path directory = Paths.get(localDirectory);
        Files.createDirectories(directory);
        String filename = sha256 + safeExtension(file.filename());
        Path destination = directory.resolve(filename);
        if (!Files.exists(destination)) {
            Files.write(destination, file.bytes());
        }
        return new StoredEvidence("LOCAL", null, filename);
    }

    private RestClient pocketBaseClient() {
        return RestClient.builder().baseUrl(pocketBaseUrl.replaceAll("/+$", "")).build();
    }

    private void requirePocketBaseConfiguration() {
        boolean hasLegacyToken = token != null && !token.isBlank();
        boolean hasServiceCredentials = pocketBaseSuperuserEmail != null
                && !pocketBaseSuperuserEmail.isBlank()
                && pocketBaseSuperuserPassword != null
                && !pocketBaseSuperuserPassword.isBlank();
        if (!hasLegacyToken && !hasServiceCredentials) {
            throw new IllegalStateException(
                    "Falta la credencial tÃ©cnica de PocketBase. Configure POCKETBASE_SUPERUSER_EMAIL y POCKETBASE_SUPERUSER_PASSWORD.");
        }
    }

    /**
     * La instancia de PocketBase dentro de Docker tiene su propia base de datos.
     * Por ello no reutilizamos a ciegas el token de una instalaciÃ³n local anterior:
     * si existen credenciales tÃ©cnicas, se obtiene un token vÃ¡lido para esta instancia.
     */
    private String pocketBaseAuthorization() {
        if (cachedAuthorization != null && !cachedAuthorization.isBlank()) {
            return cachedAuthorization;
        }

        if (pocketBaseSuperuserEmail != null && !pocketBaseSuperuserEmail.isBlank()
                && pocketBaseSuperuserPassword != null && !pocketBaseSuperuserPassword.isBlank()) {
            JsonNode auth = pocketBaseClient().post()
                    .uri("/api/collections/_superusers/auth-with-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "identity", pocketBaseSuperuserEmail,
                            "password", pocketBaseSuperuserPassword
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            String authToken = requiredText(auth, "token");
            cachedAuthorization = "Bearer " + authToken;
            return cachedAuthorization;
        }

        // Compatibilidad para instalaciones previas que ya usan un token vÃ¡lido.
        return token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("PocketBase no devolvió el campo requerido: " + field);
        }
        return value;
    }

    private String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "evidencia.bin" : filename;
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String safeExtension(String filename) {
        String safe = safeFilename(filename);
        int dot = safe.lastIndexOf('.');
        return dot >= 0 ? safe.substring(dot).toLowerCase() : ".bin";
    }

    private String normalizedContentType(EvidenciaDto file) {
        return file.contentType() == null || file.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.contentType();
    }

    public record StoredEvidence(String provider, String recordId, String filename) {
    }
}
