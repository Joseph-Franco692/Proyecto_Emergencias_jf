package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.BitacoraUnidad;
import com.bomberos.emergencias.models.EstadoUnidad;
import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.models.LecturaIot;
import com.bomberos.emergencias.models.SesionIot;
import com.bomberos.emergencias.models.UnidadBomberil;
import com.bomberos.emergencias.repositories.BitacoraUnidadRepository;
import com.bomberos.emergencias.repositories.LecturaIotRepository;
import com.bomberos.emergencias.repositories.ReporteCiudadanoRepository;
import com.bomberos.emergencias.repositories.SesionIotRepository;
import com.bomberos.emergencias.repositories.UnidadBomberilRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UnidadBomberilService {

    private static final long SEGUNDOS_PRESENCIA_OPERADOR = 45;

    @Autowired
    private UnidadBomberilRepository unidadRepository;

    @Autowired
    private BitacoraUnidadRepository bitacoraRepository;

    @Autowired
    private ReporteCiudadanoRepository reporteRepository;

    @Autowired
    private SesionIotRepository sesionIotRepository;

    @Autowired
    private LecturaIotRepository lecturaIotRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Devuelve todas las unidades en estado DISPONIBLE para poblar el modal de despacho.
     */
    public List<UnidadBomberil> obtenerUnidadesDisponibles() {
        List<UnidadBomberil> unidades =
                unidadRepository.findByEstadoAndOperadorUltimoHeartbeatAfter(
                        EstadoUnidad.DISPONIBLE,
                        LocalDateTime.now().minusSeconds(SEGUNDOS_PRESENCIA_OPERADOR)
                );
        // Desvinculamos el reporte asignado para evitar serialización circular
        unidades.forEach(u -> u.setReporteAsignado(null));
        return unidades;
    }

    /**
     * Devuelve todas las unidades registradas en el sistema.
     */
    public List<UnidadBomberil> obtenerTodasLasUnidades() {
        List<UnidadBomberil> unidades = unidadRepository.findAll();
        unidades.forEach(u -> u.setReporteAsignado(null));
        return unidades;
    }

    public UnidadBomberil obtenerPorId(Long id) {
        return unidadRepository.findById(id).orElse(null);
    }

    @Transactional
    public UnidadBomberil activarOperador(Long unidadId, String email, String nombre) {
        UnidadBomberil unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + unidadId));

        boolean otroOperadorActivo = unidad.getOperadorEmail() != null
                && !email.equalsIgnoreCase(unidad.getOperadorEmail())
                && presenciaOperadorVigente(unidad);
        if (otroOperadorActivo) {
            throw new RuntimeException("La unidad ya está ocupada por otro operador conectado.");
        }
        if (unidad.getEstado() != EstadoUnidad.DISPONIBLE
                && !email.equalsIgnoreCase(unidad.getOperadorEmail())) {
            throw new RuntimeException("La unidad ya está atendiendo una emergencia.");
        }

        unidad.setOperadorEmail(email);
        unidad.setOperadorNombre(nombre);
        unidad.setOperadorUltimoHeartbeat(LocalDateTime.now());
        UnidadBomberil guardada = unidadRepository.save(unidad);
        difundirActualizacionGeneral();
        return guardada;
    }

    @Transactional
    public UnidadBomberil registrarHeartbeat(Long unidadId, String email) {
        UnidadBomberil unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + unidadId));
        if (unidad.getOperadorEmail() == null
                || !email.equalsIgnoreCase(unidad.getOperadorEmail())) {
            throw new RuntimeException("El operador no está registrado en esta unidad.");
        }
        unidad.setOperadorUltimoHeartbeat(LocalDateTime.now());
        return unidadRepository.save(unidad);
    }

    @Transactional
    public void desactivarOperador(Long unidadId, String email) {
        UnidadBomberil unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + unidadId));
        if (unidad.getEstado() != EstadoUnidad.DISPONIBLE) {
            throw new RuntimeException("No se puede abandonar una unidad durante una emergencia activa.");
        }
        if (unidad.getOperadorEmail() != null
                && email.equalsIgnoreCase(unidad.getOperadorEmail())) {
            unidad.setOperadorEmail(null);
            unidad.setOperadorNombre(null);
            unidad.setOperadorUltimoHeartbeat(null);
            unidadRepository.save(unidad);
            difundirActualizacionGeneral();
        }
    }

    /**
     * Despacha una lista de unidades hacia un reporte de emergencia de forma @Transactional.
     * Transición ACID: DISPONIBLE → EN_RUTA para cada unidad.
     */
    @Transactional
    public Map<String, Object> despacharUnidades(Long reporteId, List<Long> unidadIds) {
        ReporteCiudadano reporte = reporteRepository.findById(reporteId)
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado con ID: " + reporteId));

        List<Map<String, Object>> unidadesDespachadas = new ArrayList<>();

        for (Long unidadId : unidadIds) {
            UnidadBomberil unidad = unidadRepository.findById(unidadId)
                    .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + unidadId));

            if (unidad.getEstado() != EstadoUnidad.DISPONIBLE) {
                throw new RuntimeException("La unidad " + unidad.getNombre() + " ya no está disponible.");
            }

            // Transición de estados (ACID garantizada por @Transactional)
            if (!presenciaOperadorVigente(unidad)) {
                throw new RuntimeException("La unidad " + unidad.getNombre()
                        + " no tiene un operador conectado en espera.");
            }

            unidad.setEstado(EstadoUnidad.EN_RUTA);
            unidad.setReporteAsignado(reporte);
            unidadRepository.save(unidad);

            // Construimos payload seguro para WebSocket (sin referencia circular)
            Map<String, Object> unidadPayload = new HashMap<>();
            unidadPayload.put("id", unidad.getId());
            unidadPayload.put("nombre", unidad.getNombre());
            unidadPayload.put("tipo", unidad.getTipo());
            unidadPayload.put("estado", unidad.getEstado().name());
            unidadPayload.put("reporteId", reporteId);
            unidadesDespachadas.add(unidadPayload);
        }

        // Payload de notificación para el dashboard central y el módulo de unidades
        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "DESPACHO");
        notificacion.put("reporteId", reporteId);
        notificacion.put("latitud", reporte.getLatitud());
        notificacion.put("longitud", reporte.getLongitud());
        notificacion.put("descripcion", reporte.getDescripcion());
        notificacion.put("celularReportero", reporte.getCelularReportero() != null ? reporte.getCelularReportero() : "");
        notificacion.put("unidades", unidadesDespachadas);
        log.info("--- DIFUNDIENDO DESPACHO VIA WEBSOCKET: {} ---", notificacion);

        // Difundir evento al topic de unidades en tiempo real
        messagingTemplate.convertAndSend("/topic/unidades-estado", (Object) notificacion);

        // Payload de respuesta limpio
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("reporteId", reporteId);
        respuesta.put("unidadesDespachadas", unidadesDespachadas);
        respuesta.put("mensaje", "Despacho ejecutado con éxito. " + unidadIds.size() + " unidad(es) en ruta.");
        return respuesta;
    }

    /**
     * Libera una unidad (EN_RUTA o EN_SITIO → DISPONIBLE) y cierra el reporte si es la última en retirarse.
     */
    @Transactional
    public Map<String, Object> disponibilizarUnidad(Long unidadId, String operador, String personal, String novedades) {
        UnidadBomberil unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + unidadId));

        ReporteCiudadano reporteAnterior = unidad.getReporteAsignado();
        Long reporteId = reporteAnterior != null ? reporteAnterior.getId() : null;
        cerrarSesionIotActiva(reporteId, unidadId);

        // Crear la bitácora final
        BitacoraUnidad bitacora = new BitacoraUnidad();
        bitacora.setUnidad(unidad);
        bitacora.setReporte(reporteAnterior);
        bitacora.setOperador(operador);
        bitacora.setPersonalInvolucrado(personal);
        bitacora.setNovedades(novedades + construirResumenIot(reporteId));
        bitacora.setFechaHora(java.time.LocalDateTime.now());
        BitacoraUnidad bitacoraGuardada = bitacoraRepository.save(bitacora);

        // Notificar nueva bitácora al dashboard central
        messagingTemplate.convertAndSend("/topic/unidades-estado", 
            (Object) Map.of("tipo", "NUEVO_REPORTE_FINAL", "bitacoraId", bitacoraGuardada.getId()));

        // Liberar la unidad
        unidad.setEstado(EstadoUnidad.DISPONIBLE);
        unidad.setReporteAsignado(null);
        unidadRepository.save(unidad);

        boolean reporteCerrado = false;

        // Verificar si es la última unidad en retirarse del reporte
        if (reporteId != null) {
            List<UnidadBomberil> unidadesRestantes = unidadRepository.findByReporteAsignadoId(reporteId);
            if (unidadesRestantes.isEmpty()) {
                // No quedan unidades asignadas: cerrar el incidente
                reporteAnterior.setIncidente(null); // Evitar circular JSON
                // En este sistema guardamos el estado de "CERRADO" sólo en WS
                // (la entidad ReporteCiudadano no tiene campo estado todavía)
                reporteCerrado = true;
                log.info("--- INCIDENTE #{} SIN UNIDADES: MARCANDO COMO ATENDIDO ---", reporteId);
            }
        }

        // Payload de unidad para WebSocket
        Map<String, Object> unidadPayload = new HashMap<>();
        unidadPayload.put("id", unidad.getId());
        unidadPayload.put("nombre", unidad.getNombre());
        unidadPayload.put("tipo", unidad.getTipo());
        unidadPayload.put("estado", unidad.getEstado().name());
        unidadPayload.put("reporteId", null);

        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "LIBERACION");
        notificacion.put("unidad", unidadPayload);
        notificacion.put("reporteAnteriorId", reporteId);
        notificacion.put("reporteCerrado", reporteCerrado);

        log.info("--- DIFUNDIENDO LIBERACION VIA WEBSOCKET: {} ---", notificacion);
        messagingTemplate.convertAndSend("/topic/unidades-estado", (Object) notificacion);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("unidadId", unidadId);
        respuesta.put("nombre", unidad.getNombre());
        respuesta.put("estadoNuevo", EstadoUnidad.DISPONIBLE.name());
        respuesta.put("reporteAnteriorId", reporteId);
        respuesta.put("reporteCerrado", reporteCerrado);
        return respuesta;
    }

    private String construirResumenIot(Long reporteId) {
        if (reporteId == null) return "";

        SesionIot sesion = sesionIotRepository
                .findFirstByReporteIdOrderByFechaInicioDesc(reporteId)
                .orElse(null);
        if (sesion == null) return "";

        LecturaIot primera = lecturaIotRepository
                .findFirstBySesionIdOrderByFechaHoraAsc(sesion.getId())
                .orElse(null);
        LecturaIot ultima = lecturaIotRepository
                .findFirstBySesionIdOrderByFechaHoraDesc(sesion.getId())
                .orElse(null);
        if (primera == null || ultima == null) return "";

        return "\n\nEvaluación IoT post-incendio"
                + " · Nodo: " + sesion.getNodoId()
                + " · Sesión: " + sesion.getId()
                + "\nPrimera toma: " + describirLectura(primera)
                + "\nÚltima toma: " + describirLectura(ultima);
    }

    private void cerrarSesionIotActiva(Long reporteId, Long unidadId) {
        if (reporteId == null) return;
        sesionIotRepository
                .findFirstByReporteIdAndEstadoOrderByFechaInicioDesc(reporteId, "ACTIVA")
                .filter(sesion -> unidadId.equals(sesion.getUnidadId()))
                .ifPresent(sesion -> {
                    LecturaIot ultima = lecturaIotRepository
                            .findFirstBySesionIdOrderByFechaHoraDesc(sesion.getId())
                            .orElse(null);
                    sesion.setEstado("FINALIZADA");
                    sesion.setFechaFin(LocalDateTime.now());
                    sesion.setResultadoFinal(ultima != null
                            ? ultima.getEvaluacionHabitabilidad()
                            : "FINALIZADA_SIN_LECTURAS");
                    sesionIotRepository.save(sesion);
                });
    }

    private String describirLectura(LecturaIot lectura) {
        String hora = lectura.getFechaHora() != null
                ? lectura.getFechaHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                : "sin hora";
        return "gas " + lectura.getNivelGas()
                + " · aire " + lectura.getEstadoAire()
                + " · resultado " + lectura.getEvaluacionHabitabilidad()
                + " · " + hora;
    }

    /**
     * Actualiza el estado de una unidad a EN_SITIO cuando el camión confirma llegada.
     */
    @Transactional
    public Map<String, Object> marcarEnSitio(Long unidadId) {
        UnidadBomberil unidad = unidadRepository.findById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada: " + unidadId));

        unidad.setEstado(EstadoUnidad.EN_SITIO);
        unidadRepository.save(unidad);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", unidad.getId());
        payload.put("nombre", unidad.getNombre());
        payload.put("estado", EstadoUnidad.EN_SITIO.name());
        payload.put("tipo", "LLEGADA_SITIO");

        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "LLEGADA_SITIO");
        notificacion.put("unidad", payload);
        messagingTemplate.convertAndSend("/topic/unidades-estado", (Object) notificacion);

        return payload;
    }

    /**
     * Inicializa las unidades predeterminadas del parque bomberil si la base de datos está vacía.
     */
    @Transactional
    public void inicializarUnidadesPredeterminadas() {
        if (unidadRepository.count() == 0) {
            List<UnidadBomberil> unidadesPredeterminadas = List.of(
                createUnidad("B-01 Autobomba", "Ataque contra incendios"),
                createUnidad("B-02 Autobomba", "Ataque contra incendios"),
                createUnidad("B-03 Escalera Aérea", "Rescate en altura"),
                createUnidad("R-07 Rescate", "Rescate vehicular y vial"),
                createUnidad("HZ-02 Hazmat", "Materiales peligrosos"),
                createUnidad("AM-01 Ambulancia", "Soporte vital básico")
            );
            unidadRepository.saveAll(unidadesPredeterminadas);
            log.info("--- UNIDADES PREDETERMINADAS INICIALIZADAS: {} ---", unidadesPredeterminadas.size());
        }
    }

    private UnidadBomberil createUnidad(String nombre, String tipo) {
        UnidadBomberil u = new UnidadBomberil();
        u.setNombre(nombre);
        u.setTipo(tipo);
        u.setEstado(EstadoUnidad.DISPONIBLE);
        return u;
    }

    /**
     * Crear una nueva unidad
     */
    @Transactional
    public UnidadBomberil crearUnidad(UnidadBomberil unidad) {
        unidad.setEstado(EstadoUnidad.DISPONIBLE);
        unidad.setReporteAsignado(null);
        UnidadBomberil guardada = unidadRepository.save(unidad);
        difundirActualizacionGeneral();
        return guardada;
    }

    /**
     * Eliminar una unidad existente
     */
    @Transactional
    public void eliminarUnidad(Long id) {
        UnidadBomberil unidad = unidadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + id));
        unidadRepository.delete(unidad);
        difundirActualizacionGeneral();
    }

    public List<BitacoraUnidad> obtenerBitacoras() {
        return bitacoraRepository.findAllByOrderByFechaHoraDesc();
    }

    /**
     * Cambiar manualmente el estado de una unidad (Forzado desde Central)
     */
    @Transactional
    public UnidadBomberil cambiarEstadoManual(Long id, EstadoUnidad nuevoEstado) {
        UnidadBomberil unidad = unidadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + id));
        
        unidad.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoUnidad.DISPONIBLE) {
            unidad.setReporteAsignado(null); // Liberar si se pone disponible
            
            // Notificar al dashboard de la unidad que ha sido liberada
            Map<String, Object> liberacionEvent = new HashMap<>();
            liberacionEvent.put("tipo", "LIBERACION");
            Map<String, Object> uMap = new HashMap<>();
            uMap.put("id", unidad.getId());
            liberacionEvent.put("unidad", uMap);
            messagingTemplate.convertAndSend("/topic/unidades-estado", (Object) liberacionEvent);
        }
        UnidadBomberil actualizada = unidadRepository.save(unidad);
        difundirActualizacionGeneral();
        return actualizada;
    }

    /**
     * Difundir actualización general para refrescar las listas en la central
     */
    private void difundirActualizacionGeneral() {
        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "ACTUALIZACION_INVENTARIO");
        messagingTemplate.convertAndSend("/topic/unidades-estado", (Object) notificacion);
    }

    private boolean presenciaOperadorVigente(UnidadBomberil unidad) {
        return unidad.getOperadorEmail() != null
                && unidad.getOperadorUltimoHeartbeat() != null
                && unidad.getOperadorUltimoHeartbeat().isAfter(
                        LocalDateTime.now().minusSeconds(SEGUNDOS_PRESENCIA_OPERADOR)
                );
    }
}
