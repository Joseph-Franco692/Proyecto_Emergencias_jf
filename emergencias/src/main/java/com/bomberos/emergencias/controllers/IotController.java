package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.models.LecturaIot;
import com.bomberos.emergencias.models.SesionIot;
import com.bomberos.emergencias.models.UnidadBomberil;
import com.bomberos.emergencias.repositories.LecturaIotRepository;
import com.bomberos.emergencias.repositories.SesionIotRepository;
import com.bomberos.emergencias.repositories.UnidadBomberilRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iot")
@CrossOrigin(origins = "*")
public class IotController {

    private static final int UMBRAL_ADVERTENCIA_PREDETERMINADO = 2500;
    private static final int UMBRAL_PELIGRO_PREDETERMINADO = 2600;
    private static final int LECTURAS_ESTABLES_REQUERIDAS = 5;

    private final LecturaIotRepository lecturaRepository;
    private final SesionIotRepository sesionRepository;
    private final UnidadBomberilRepository unidadRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${iot.node-key:CAMBIAR-CLAVE-NODO-2026}")
    private String nodeKey;

    public IotController(
            LecturaIotRepository lecturaRepository,
            SesionIotRepository sesionRepository,
            UnidadBomberilRepository unidadRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.lecturaRepository = lecturaRepository;
        this.sesionRepository = sesionRepository;
        this.unidadRepository = unidadRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public static class IniciarSesionPayload {
        public Long unidadId;
        public String nodoId;
    }

    @PostMapping("/sesiones/iniciar")
    public ResponseEntity<?> iniciarSesion(
            @RequestBody IniciarSesionPayload payload,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Sesión de operador requerida"));
        }
        if (payload == null || payload.unidadId == null || payload.nodoId == null || payload.nodoId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "unidadId y nodoId son obligatorios"));
        }

        UnidadBomberil unidad = unidadRepository.findById(payload.unidadId)
                .orElse(null);
        if (unidad == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unidad no encontrada"));
        }
        if (unidad.getReporteAsignado() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "La unidad no tiene una emergencia activa"));
        }

        String nodoId = payload.nodoId.trim().toUpperCase();
        Long reporteId = unidad.getReporteAsignado().getId();
        var existenteNodo = sesionRepository
                .findFirstByNodoIdAndEstadoOrderByFechaInicioDesc(nodoId, "ACTIVA");
        if (existenteNodo.isPresent()) {
            SesionIot anterior = existenteNodo.get();
            if (reporteId.equals(anterior.getReporteId())) {
                return ResponseEntity.ok(respuestaSesion(anterior));
            }
            boolean emergenciaAnteriorSigueActiva =
                    !unidadRepository.findByReporteAsignadoId(anterior.getReporteId()).isEmpty();
            if (emergenciaAnteriorSigueActiva) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "El nodo ya está vinculado a otra evaluación activa"
                ));
            }
            anterior.setEstado("FINALIZADA");
            anterior.setResultadoFinal("FINALIZADA_SIN_LECTURA_FINAL");
            anterior.setFechaFin(LocalDateTime.now());
            sesionRepository.save(anterior);
        }

        var existenteReporte = sesionRepository
                .findFirstByReporteIdAndEstadoOrderByFechaInicioDesc(reporteId, "ACTIVA");
        if (existenteReporte.isPresent()) {
            return ResponseEntity.ok(respuestaSesion(existenteReporte.get()));
        }

        SesionIot sesion = new SesionIot();
        sesion.setNodoId(nodoId);
        sesion.setReporteId(reporteId);
        sesion.setUnidadId(unidad.getId());
        sesion.setOperador(authentication.getName());
        sesion = sesionRepository.save(sesion);

        messagingTemplate.convertAndSend("/topic/iot-sesiones", (Object) Map.of(
                "tipo", "SESION_IOT_INICIADA",
                "reporteId", reporteId,
                "nodoId", nodoId,
                "sesionId", sesion.getId()
        ));
        return ResponseEntity.ok(respuestaSesion(sesion));
    }

    @PostMapping("/telemetria")
    public ResponseEntity<?> recibirTelemetria(
            @RequestHeader(value = "X-IOT-KEY", required = false) String providedKey,
            @RequestBody LecturaIot lectura
    ) {
        if (providedKey == null || !providedKey.equals(nodeKey)) {
            return ResponseEntity.status(401).body(Map.of("error", "Credencial del nodo inválida"));
        }
        if (lectura.getNodoId() == null || lectura.getNodoId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "nodoId es obligatorio"));
        }
        if (lectura.getNivelGas() == null || lectura.getNivelGas() < 0 || lectura.getNivelGas() > 4095) {
            return ResponseEntity.badRequest().body(Map.of("error", "nivelGas debe estar entre 0 y 4095"));
        }
        if (lectura.getUmbralAdvertencia() == null) {
            lectura.setUmbralAdvertencia(UMBRAL_ADVERTENCIA_PREDETERMINADO);
        }
        if (lectura.getUmbralPeligro() == null) {
            lectura.setUmbralPeligro(UMBRAL_PELIGRO_PREDETERMINADO);
        }
        if (lectura.getUmbralAdvertencia() < 0
                || lectura.getUmbralPeligro() <= lectura.getUmbralAdvertencia()
                || lectura.getUmbralPeligro() > 4095) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Los umbrales deben cumplir 0 <= advertencia < peligro <= 4095"
            ));
        }

        String nodoId = lectura.getNodoId().trim().toUpperCase();
        SesionIot sesion = sesionRepository
                .findFirstByNodoIdAndEstadoOrderByFechaInicioDesc(nodoId, "ACTIVA")
                .orElse(null);
        if (sesion == null) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "El operador todavía no ha iniciado una sesión para este nodo"
            ));
        }

        lectura.setNodoId(nodoId);
        lectura.setSesionId(sesion.getId());
        lectura.setReporteId(sesion.getReporteId());
        lectura.setUnidadId(sesion.getUnidadId());
        lectura.setOperador(sesion.getOperador());
        lectura.setEvento(normalizarEvento(lectura.getEvento()));
        lectura.setEstadoAire(calcularEstadoAire(lectura));
        lectura.setEvaluacionHabitabilidad(calcularEvaluacionInicial(lectura));
        LecturaIot guardada = lecturaRepository.save(lectura);

        if ("RESPIRABLE".equals(guardada.getEstadoAire()) && sesionEstable(sesion.getId())) {
            guardada.setEvaluacionHabitabilidad("HABITABLE");
            guardada = lecturaRepository.save(guardada);
        }

        if ("FIN_SESION".equals(guardada.getEvento())) {
            sesion.setEstado("FINALIZADA");
            sesion.setResultadoFinal(guardada.getEvaluacionHabitabilidad());
            sesion.setFechaFin(LocalDateTime.now());
            sesionRepository.save(sesion);
        }

        messagingTemplate.convertAndSend("/topic/iot-telemetria", guardada);
        return ResponseEntity.ok(guardada);
    }

    @GetMapping("/reportes/{reporteId}/bitacora")
    public ResponseEntity<List<LecturaIot>> obtenerBitacoraReporte(@PathVariable Long reporteId) {
        return ResponseEntity.ok(lecturaRepository.findByReporteIdOrderByFechaHoraDesc(reporteId));
    }

    @GetMapping("/reportes/{reporteId}/sesiones")
    public ResponseEntity<List<SesionIot>> obtenerSesionesReporte(@PathVariable Long reporteId) {
        return ResponseEntity.ok(sesionRepository.findByReporteIdOrderByFechaInicioDesc(reporteId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "OK", "modulo", "IoT Telemetry Service"));
    }

    private Map<String, Object> respuestaSesion(SesionIot sesion) {
        Map<String, Object> response = new HashMap<>();
        response.put("sesionId", sesion.getId());
        response.put("nodoId", sesion.getNodoId());
        response.put("reporteId", sesion.getReporteId());
        response.put("unidadId", sesion.getUnidadId());
        response.put("operador", sesion.getOperador());
        response.put("estado", sesion.getEstado());
        response.put("mensaje", "Nodo vinculado. Encienda el dispositivo para comenzar la evaluación.");
        return response;
    }

    private String normalizarEvento(String evento) {
        if ("INICIO_SESION".equals(evento) || "FIN_SESION".equals(evento)) {
            return evento;
        }
        return "TELEMETRIA";
    }

    private String calcularEstadoAire(LecturaIot lectura) {
        if (lectura.getNivelGas() >= lectura.getUmbralPeligro()) return "CRITICO";
        if (lectura.getNivelGas() >= lectura.getUmbralAdvertencia()) return "PRECAUCION";
        return "RESPIRABLE";
    }

    private String calcularEvaluacionInicial(LecturaIot lectura) {
        if ("CRITICO".equals(lectura.getEstadoAire())) return "NO_HABITABLE";
        return "EVALUANDO";
    }

    private boolean sesionEstable(Long sesionId) {
        List<LecturaIot> recientes =
                lecturaRepository.findTop5BySesionIdOrderByFechaHoraDesc(sesionId);
        return recientes.size() >= LECTURAS_ESTABLES_REQUERIDAS
                && recientes.stream().allMatch(l ->
                        l.getUmbralAdvertencia() != null
                                && l.getNivelGas() < l.getUmbralAdvertencia()
                );
    }
}
