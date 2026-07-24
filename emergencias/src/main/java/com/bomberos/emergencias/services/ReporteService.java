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
import java.util.List;
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

    private final String CARPETA_UPLOADS = "uploads/";

    @Transactional
    public ReporteCiudadano registrarYNotificar(ReporteCiudadano reporte) {
        // 1. Guardamos de forma segura en Postgres
        ReporteCiudadano guardado = reporteRepository.save(reporte);

        // 2. CONSTRUIMOS UN PAYLOAD MAP LIMPIO
        String jsonPayload = "{" +
                "\"id\":" + guardado.getId() + "," +
                "\"descripcion\":\"" + guardado.getDescripcion().replace("\"", "\\\"") + "\"," +
                "\"latitud\":\"" + guardado.getLatitud().toString() + "\"," +
                "\"longitud\":\"" + guardado.getLongitud().toString() + "\"," +
                "\"celularReportero\":\"" + (guardado.getCelularReportero() != null ? guardado.getCelularReportero() : "") + "\"," +
                "\"iaLabel\":\"" + (guardado.getIaLabel() != null ? guardado.getIaLabel() : "") + "\"," +
                "\"iaConfidence\":\"" + (guardado.getIaConfidence() != null ? guardado.getIaConfidence().toString() : "") + "\"," +
                "\"fechaReporte\":\"" + guardado.getFechaReporte().toString() + "\"" +
                "}";

        log.info("--- TRANSMITIENDO EVOLUCIÓN WEBSOCKET: {} ---", jsonPayload);

        // CAMBIO AQUÍ: Enviamos el payload DIRECTO, sin el Optional.of()
        // Le hacemos un cast a (Object) para que IntelliJ sepa exactamente qué método usar
        messagingTemplate.convertAndSend("/topic/nuevos-reportes", (Object) jsonPayload);

        return guardado;
    }


    @Async
    public void guardarEvidenciasMultimediaAsincrono(ReporteCiudadano reporte, List<EvidenciaDto> archivos) {
        if (archivos == null || archivos.isEmpty()) return;

        try {
            Files.createDirectories(Paths.get(CARPETA_UPLOADS));
        } catch (IOException e) {
            log.error("Error creando carpeta: {}", e.getMessage());
        }

        for (EvidenciaDto archivo : archivos) {
            try {
                String nombreUnico = UUID.randomUUID().toString() + "_" + archivo.filename();
                Path rutaCompleta = Paths.get(CARPETA_UPLOADS + nombreUnico);
                
                // Escribir los bytes directamente en el archivo
                Files.write(rutaCompleta, archivo.bytes());

                EvidenciaMultimedia evidencia = new EvidenciaMultimedia();
                evidencia.setReporteCiudadano(reporte);
                evidencia.setUrlArchivo(rutaCompleta.toString());
                evidencia.setTipoArchivo(archivo.contentType() != null && archivo.contentType().contains("video") ? "VIDEO" : "FOTO");

                evidenciaRepository.save(evidencia);
                log.info("--- HILO SECUNDARIO (Async): Archivo guardado con éxito: {} ---", nombreUnico);
            } catch (IOException e) {
                log.error("Error en segundo plano: {}", e.getMessage());
            }
        }
    }

    /**
     * Consulta el historial completo de reportes ciudadanos en la base de datos.
     */
    public List<ReporteCiudadano> obtenerTodosLosReportes() {
        return reporteRepository.findAll(); // Usa el método nativo de JPA para hacer un "SELECT * FROM"
    }

    public Optional<ReporteCiudadano> obtenerReportePorId(Long id) {
        return reporteRepository.findById(id);
    }

    public List<EvidenciaMultimedia> obtenerEvidenciasPorReporteId(Long id) {
        return evidenciaRepository.findByReporteCiudadanoId(id);
    }

}