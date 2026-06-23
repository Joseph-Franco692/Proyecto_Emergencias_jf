package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.models.EvidenciaMultimedia;
import com.bomberos.emergencias.models.EvidenciaDto;
import com.bomberos.emergencias.services.ReporteService;
import com.bomberos.emergencias.services.UnidadBomberilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin(origins = "*")
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private UnidadBomberilService unidadService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("taskExecutor")
    private Executor taskExecutor;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> crearReporte(
            @RequestParam("descripcion") String descripcion,
            @RequestParam("latitud") String latitud,
            @RequestParam("longitud") String longitud,
            @RequestParam("celularReportero") String celularReportero,
            @RequestParam(value = "imagenes", required = false) MultipartFile[] fotos) {

        ReporteCiudadano nuevoReporte = new ReporteCiudadano();
        nuevoReporte.setDescripcion(descripcion);
        nuevoReporte.setLatitud(new BigDecimal(latitud));
        nuevoReporte.setLongitud(new BigDecimal(longitud));
        nuevoReporte.setCelularReportero(celularReportero);

        // 1. Guardamos en la base de datos
        ReporteCiudadano reporteGuardado = reporteService.registrarYNotificar(nuevoReporte);

        // Convertimos las fotos a DTOs con sus bytes leídos en el hilo principal
        List<EvidenciaDto> evidenciasDto = new ArrayList<>();
        if (fotos != null) {
            for (MultipartFile foto : fotos) {
                if (foto != null && !foto.isEmpty()) {
                    try {
                        evidenciasDto.add(new EvidenciaDto(
                            foto.getOriginalFilename(),
                            foto.getContentType(),
                            foto.getBytes()
                        ));
                    } catch (IOException e) {
                        System.err.println("Error leyendo bytes del archivo en el hilo principal: " + e.getMessage());
                    }
                }
            }
        }

        // 2. Procesamos las fotos en el hilo secundario asincrónico pasándole los bytes cargados
        reporteService.guardarEvidenciasMultimediaAsincrono(reporteGuardado, evidenciasDto);

        // Retornamos la respuesta como Map para evitar cualquier error de serialización Jackson con la Entidad JPA
        Map<String, Object> response = new HashMap<>();
        response.put("id", reporteGuardado.getId());
        response.put("mensaje", "Reporte creado exitosamente");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping // Responde a: GET http://localhost:8081/api/reportes
    public ResponseEntity<List<ReporteCiudadano>> listarHistorial() {
        List<ReporteCiudadano> historial = reporteService.obtenerTodosLosReportes();

        // Rompemos cualquier bucle de persistencia perezosa antes de serializar a JSON
        for (ReporteCiudadano reporte : historial) {
            reporte.setIncidente(null); // Desvinculamos el objeto incidente para evitar bloqueos de Jackson
        }

        return ResponseEntity.ok(historial);
    }

    /**
     * PUT /api/reportes/{id}/despachar
     * Recibe lista de IDs de unidades y las asigna transaccionalmente al reporte.
     */
    @PutMapping("/{id}/despachar")
    public ResponseEntity<Map<String, Object>> despacharUnidades(
            @PathVariable Long id,
            @RequestBody List<Long> unidadIds) {
        try {
            Map<String, Object> resultado = unidadService.despacharUnidades(id, unidadIds);
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReporteCiudadano> obtenerReporte(@PathVariable Long id) {
        return reporteService.obtenerReportePorId(id)
                .map(reporte -> {
                    // Evitamos bucles infinitos de Jackson al convertir a JSON
                    reporte.setIncidente(null);
                    return ResponseEntity.ok(reporte);
                })
                .orElse(ResponseEntity.notFound().build()); // Si no existe, devuelve Error 404
    }

    @GetMapping("/{id}/evidencias")
    public ResponseEntity<List<EvidenciaMultimedia>> obtenerEvidencias(@PathVariable Long id) {
        List<EvidenciaMultimedia> evidencias = reporteService.obtenerEvidenciasPorReporteId(id);
        // Rompemos referencia circular
        for (EvidenciaMultimedia ev : evidencias) {
            ev.setReporteCiudadano(null);
        }
        return ResponseEntity.ok(evidencias);
    }

    @GetMapping("/stats/hilos")
    public ResponseEntity<Map<String, Object>> obtenerEstadisticasHilos() {
        Map<String, Object> stats = new HashMap<>();
        if (taskExecutor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) taskExecutor;
            stats.put("activeCount", pool.getActiveCount());
            stats.put("poolSize", pool.getPoolSize());
            stats.put("corePoolSize", pool.getCorePoolSize());
            stats.put("maxPoolSize", pool.getMaxPoolSize());
            stats.put("queueSize", pool.getQueueSize());
        } else {
            stats.put("activeCount", 0);
            stats.put("poolSize", 0);
            stats.put("corePoolSize", 4);
            stats.put("maxPoolSize", 8);
            stats.put("queueSize", 0);
        }
        return ResponseEntity.ok(stats);
    }

}