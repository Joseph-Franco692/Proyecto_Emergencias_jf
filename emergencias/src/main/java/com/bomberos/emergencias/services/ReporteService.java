package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.EvidenciaMultimedia;
import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.models.EvidenciaDto;
import com.bomberos.emergencias.repositories.EvidenciaMultimediaRepository;
import com.bomberos.emergencias.repositories.ReporteCiudadanoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ReporteService {

    @Autowired
    private ReporteCiudadanoRepository reporteRepository;

    @Autowired
    private EvidenciaMultimediaRepository evidenciaRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final String CARPETA_UPLOADS = "uploads/";

    @Transactional
    public ReporteCiudadano registrarYNotificar(ReporteCiudadano reporte) {
        ReporteCiudadano guardado = reporteRepository.save(reporte);

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("id", guardado.getId());
        payloadMap.put("descripcion", guardado.getDescripcion() != null ? guardado.getDescripcion() : "");
        payloadMap.put("latitud", guardado.getLatitud() != null ? guardado.getLatitud().toString() : "0");
        payloadMap.put("longitud", guardado.getLongitud() != null ? guardado.getLongitud().toString() : "0");
        payloadMap.put("celularReportero", guardado.getCelularReportero() != null ? guardado.getCelularReportero() : "");
        payloadMap.put("iaLabel", guardado.getIaLabel() != null ? guardado.getIaLabel() : "");
        payloadMap.put("iaConfidence", guardado.getIaConfidence() != null ? guardado.getIaConfidence().toString() : "0");
        payloadMap.put("fechaReporte", guardado.getFechaReporte() != null ? guardado.getFechaReporte().toString() : "");

        log.info("--- TRANSMITIENDO EVOLUCIÓN WEBSOCKET CON PAYLOAD MAP: {} ---", payloadMap);
        messagingTemplate.convertAndSend("/topic/nuevos-reportes", (Object) payloadMap);

        return guardado;
    }

    /**
     * Guarda evidencias utilizando Almacenamiento Direccionable por Contenido (CAS).
     * Calcula el Hash SHA-256 de los bytes del archivo para:
     * 1. Evitar guardar en Base64 (Práctica recomendada en Sistemas Distribuidos).
     * 2. Garantizar deduplicación (archivos idénticos se guardan solo una vez).
     * 3. Permitir verificación de integridad entre nodos distribuidos.
     */
    public void guardarEvidenciasMultimedia(ReporteCiudadano reporte, List<EvidenciaDto> archivos) {
        guardarEvidenciasMultimediaAsincrono(reporte, archivos);
    }

    @Async
    public void guardarEvidenciasMultimediaAsincrono(ReporteCiudadano reporte, List<EvidenciaDto> archivos) {
        if (archivos == null || archivos.isEmpty()) return;

        try {
            Files.createDirectories(Paths.get(CARPETA_UPLOADS));
        } catch (IOException e) {
            log.error("Error creando carpeta uploads: {}", e.getMessage());
        }

        for (EvidenciaDto archivo : archivos) {
            try {
                if (archivo.bytes() == null || archivo.bytes().length == 0) continue;

                // 1. Calcular Hash SHA-256 del contenido binario
                String sha256Hex = calcularHashSHA256(archivo.bytes());
                String extension = extraerExtension(archivo.filename());
                String nombreHash = sha256Hex + extension;

                Path rutaCompleta = Paths.get(CARPETA_UPLOADS + nombreHash);

                // 2. Deduplicación: Si el archivo ya existe en disco, no se vuelve a escribir
                if (!Files.exists(rutaCompleta)) {
                    Files.write(rutaCompleta, archivo.bytes());
                    log.info("--- [STORAGE-HASH] Archivo nuevo guardado en disco con SHA-256: {} ---", nombreHash);
                } else {
                    log.info("--- [STORAGE-HASH] Archivo duplicado detectado por Hash SHA-256 (reutilizando): {} ---", nombreHash);
                }

                // 3. Registrar referencia en la BD (solo guarda URL relativa y Hash SHA-256)
                EvidenciaMultimedia evidencia = new EvidenciaMultimedia();
                evidencia.setReporteCiudadano(reporte);
                evidencia.setUrlArchivo("/uploads/" + nombreHash);
                evidencia.setHashSha256(sha256Hex);
                evidencia.setTipoArchivo(archivo.contentType() != null && archivo.contentType().contains("video") ? "VIDEO" : "FOTO");

                evidenciaRepository.save(evidencia);

            } catch (Exception e) {
                log.error("Error procesando evidencia con Hash SHA-256: {}", e.getMessage());
            }
        }
    }

    private String calcularHashSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String extraerExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }

    public List<ReporteCiudadano> obtenerTodosLosReportes() {
        return reporteRepository.findAll();
    }

    public Optional<ReporteCiudadano> obtenerReportePorId(Long id) {
        return reporteRepository.findById(id);
    }

    public List<EvidenciaMultimedia> obtenerEvidenciasPorReporteId(Long id) {
        return evidenciaRepository.findByReporteCiudadanoId(id);
    }
}