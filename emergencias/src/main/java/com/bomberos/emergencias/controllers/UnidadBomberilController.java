package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.models.UnidadBomberil;
import com.bomberos.emergencias.services.UnidadBomberilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unidades")
@CrossOrigin(origins = "*")
public class UnidadBomberilController {

    @Autowired
    private UnidadBomberilService unidadService;

    /**
     * GET /api/unidades → lista todas las unidades del sistema
     */
    @GetMapping
    public ResponseEntity<List<UnidadBomberil>> listarTodas() {
        return ResponseEntity.ok(unidadService.obtenerTodasLasUnidades());
    }

    /**
     * GET /api/unidades/disponibles → solo unidades con estado DISPONIBLE
     */
    @GetMapping("/disponibles")
    public ResponseEntity<List<UnidadBomberil>> listarDisponibles() {
        return ResponseEntity.ok(unidadService.obtenerUnidadesDisponibles());
    }

    public static class ReporteFinalPayload {
        public String operador;
        public String personal;
        public String novedades;
    }

    /**
     * PUT /api/unidades/{id}/disponibilizar → libera la unidad y guarda la bitácora
     */
    @PutMapping("/{id}/disponibilizar")
    public ResponseEntity<Map<String, Object>> disponibilizar(@PathVariable Long id, @RequestBody(required = false) ReporteFinalPayload payload) {
        String operador = payload != null ? payload.operador : "Desconocido";
        String personal = payload != null ? payload.personal : "Desconocido";
        String novedades = payload != null ? payload.novedades : "Sin novedades";
        
        Map<String, Object> resultado = unidadService.disponibilizarUnidad(id, operador, personal, novedades);
        return ResponseEntity.ok(resultado);
    }

    /**
     * GET /api/unidades/reportes-finales → obtiene las bitácoras ordenadas
     */
    @GetMapping("/reportes-finales")
    public ResponseEntity<List<com.bomberos.emergencias.models.BitacoraUnidad>> obtenerReportesFinales() {
        return ResponseEntity.ok(unidadService.obtenerBitacoras());
    }

    /**
     * PUT /api/unidades/{id}/en-sitio → marca la unidad como EN_SITIO al llegar al lugar
     */
    @PutMapping("/{id}/en-sitio")
    public ResponseEntity<Map<String, Object>> marcarEnSitio(@PathVariable Long id) {
        Map<String, Object> resultado = unidadService.marcarEnSitio(id);
        return ResponseEntity.ok(resultado);
    }
    /**
     * POST /api/unidades → crea una nueva unidad
     */
    @PostMapping
    public ResponseEntity<UnidadBomberil> crearUnidad(@RequestBody UnidadBomberil unidad) {
        return ResponseEntity.ok(unidadService.crearUnidad(unidad));
    }

    /**
     * DELETE /api/unidades/{id} → elimina una unidad
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminarUnidad(@PathVariable Long id) {
        unidadService.eliminarUnidad(id);
        Map<String, Object> respuesta = new java.util.HashMap<>();
        respuesta.put("mensaje", "Unidad eliminada correctamente");
        return ResponseEntity.ok(respuesta);
    }

    /**
     * PUT /api/unidades/{id}/estado → fuerza un cambio de estado manualmente
     */
    @PutMapping("/{id}/estado")
    public ResponseEntity<UnidadBomberil> cambiarEstado(@PathVariable Long id, @RequestParam com.bomberos.emergencias.models.EstadoUnidad estado) {
        return ResponseEntity.ok(unidadService.cambiarEstadoManual(id, estado));
    }
}
