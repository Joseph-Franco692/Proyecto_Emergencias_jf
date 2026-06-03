package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.services.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired
    private ReporteService reporteService; // Inyectamos el servicio con la lógica de hilos

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ReporteCiudadano> crearReporte(
            @RequestPart("descripcion") String descripcion,
            @RequestPart("latitud") String latitud,
            @RequestPart("longitud") String longitud,
            @RequestPart("celularReportero") String celularReportero,
            @RequestPart(value = "imagenes", required = false) MultipartFile[] fotos) {

        ReporteCiudadano nuevoReporte = new ReporteCiudadano();
        nuevoReporte.setDescripcion(descripcion);
        nuevoReporte.setLatitud(new BigDecimal(latitud));
        nuevoReporte.setLongitud(new BigDecimal(longitud));
        nuevoReporte.setCelularReportero(celularReportero);

        // 1. Guardamos en la base de datos
        ReporteCiudadano reporteGuardado = reporteService.registrarYNotificar(nuevoReporte);

        // 2. Procesamos las fotos en el hilo secundario
        reporteService.guardarEvidenciasMultimediaAsincrono(reporteGuardado, fotos);

        // Retornamos la respuesta
        return ResponseEntity.ok(reporteGuardado);
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

}