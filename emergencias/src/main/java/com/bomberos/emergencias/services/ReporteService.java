package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.EvidenciaDto;
import com.bomberos.emergencias.models.EvidenciaMultimedia;
import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.models.EstadoReporte;
import com.bomberos.emergencias.repositories.EvidenciaMultimediaRepository;
import com.bomberos.emergencias.repositories.ReporteCiudadanoRepository;
import com.bomberos.emergencias.repositories.BitacoraUnidadRepository;
import com.bomberos.emergencias.repositories.UnidadBomberilRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ReporteService {

    @Autowired
    private ReporteCiudadanoRepository reporteRepository;

    @Autowired
    private EvidenciaMultimediaRepository evidenciaRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private EvidenceStorageService evidenceStorageService;

    @Autowired
    private BitacoraUnidadRepository bitacoraRepository;

    @Autowired
    private UnidadBomberilRepository unidadRepository;

    /**
     * Compatibilidad con registros creados antes de incorporar el estado
     * persistente. Se ejecuta una sola vez al iniciar cada réplica y es
     * idempotente.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void normalizarEstadosLegacy() {
        List<ReporteCiudadano> pendientes = reporteRepository.findAll().stream()
                .filter(reporte -> reporte.getEstado() == null)
                .toList();
        for (ReporteCiudadano reporte : pendientes) {
            if (!unidadRepository.findByReporteAsignadoId(reporte.getId()).isEmpty()) {
                reporte.setEstado(EstadoReporte.EN_ATENCION);
                reporte.setFechaAtencion(reporte.getFechaReporte());
            } else if (bitacoraRepository.existsByReporteId(reporte.getId())) {
                reporte.setEstado(EstadoReporte.ATENDIDO);
            } else {
                reporte.setEstado(EstadoReporte.PENDIENTE);
            }
        }
        if (!pendientes.isEmpty()) {
            reporteRepository.saveAll(pendientes);
            log.info("Estados normalizados para {} reportes históricos", pendientes.size());
        }
    }

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
        payloadMap.put("estado", guardado.getEstado() != null ? guardado.getEstado().name() : "PENDIENTE");

        log.info("Transmitiendo nuevo reporte por WebSocket: {}", payloadMap);
        messagingTemplate.convertAndSend("/topic/nuevos-reportes", (Object) payloadMap);
        return guardado;
    }

    public void guardarEvidenciasMultimedia(ReporteCiudadano reporte, List<EvidenciaDto> archivos) {
        guardarEvidenciasMultimediaAsincrono(reporte, archivos);
    }

    /**
     * Calcula SHA-256 y delega el binario al proveedor configurado. PostgreSQL
     * conserva únicamente la relación, metadatos e identificadores externos.
     */
    @Async
    public void guardarEvidenciasMultimediaAsincrono(ReporteCiudadano reporte, List<EvidenciaDto> archivos) {
        if (archivos == null || archivos.isEmpty()) {
            return;
        }

        for (EvidenciaDto archivo : archivos) {
            try {
                if (archivo.bytes() == null || archivo.bytes().length == 0) {
                    continue;
                }

                String sha256Hex = calcularHashSHA256(archivo.bytes());
                EvidenceStorageService.StoredEvidence stored =
                        evidenceStorageService.store(archivo, sha256Hex, reporte.getId());

                EvidenciaMultimedia evidencia = new EvidenciaMultimedia();
                evidencia.setReporteCiudadano(reporte);
                evidencia.setStorageKey(UUID.randomUUID().toString());
                evidencia.setUrlArchivo("/api/reportes/evidencias/" + evidencia.getStorageKey() + "/archivo");
                evidencia.setHashSha256(sha256Hex);
                evidencia.setTipoArchivo(esVideo(archivo.contentType()) ? "VIDEO" : "FOTO");
                evidencia.setProveedorAlmacenamiento(stored.provider());
                evidencia.setPocketbaseRecordId(stored.recordId());
                evidencia.setPocketbaseFilename(stored.filename());
                evidencia.setMimeType(archivo.contentType());
                evidencia.setTamanoBytes((long) archivo.bytes().length);
                evidenciaRepository.save(evidencia);
            } catch (Exception e) {
                log.error("Error almacenando evidencia del reporte {}: {}", reporte.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean esVideo(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("video/");
    }

    private String calcularHashSHA256(byte[] data) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
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

    public Optional<EvidenciaMultimedia> obtenerEvidenciaPorStorageKey(String storageKey) {
        return evidenciaRepository.findByStorageKey(storageKey);
    }

    public byte[] cargarContenido(EvidenciaMultimedia evidencia) throws Exception {
        return evidenceStorageService.load(
                evidencia.getProveedorAlmacenamiento(),
                evidencia.getPocketbaseRecordId(),
                evidencia.getPocketbaseFilename(),
                evidencia.getUrlArchivo());
    }
}
