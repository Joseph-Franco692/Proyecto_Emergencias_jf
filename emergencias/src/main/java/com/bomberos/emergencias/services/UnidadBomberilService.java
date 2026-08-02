package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.BitacoraUnidad;
import com.bomberos.emergencias.models.EstadoUnidad;
import com.bomberos.emergencias.models.EstadoReporte;
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
        UnidadBomberil unidad = unidadRepository.findWithLockById(unidadId)
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
        UnidadBomberil unidad = unidadRepository.findWithLockById(unidadId)
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
        UnidadBomberil unidad = unidadRepository.findWithLockById(unidadId)
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
        if (unidadIds == null || unidadIds.isEmpty()) {
            throw new RuntimeException("Selecciona al menos una unidad para despachar.");
        }
        if (unidadIds.stream().distinct().count() != unidadIds.size()) {
            throw new RuntimeException("La selección contiene unidades duplicadas.");
        }

        ReporteCiudadano reporte = reporteRepository.findWithLockById(reporteId)
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado con ID: " + reporteId));
        if (reporte.getEstado() == EstadoReporte.ATENDIDO) {
            throw new RuntimeException("El reporte ya fue atendido y no puede volver a ser asignado.");
        }

        List<Map<String, Object>> unidadesDespachadas = new ArrayList<>();

        for (Long unidadId : unidadIds) {
            UnidadBomberil unidad = unidadRepository.findWithLockById(unidadId)
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

        if (reporte.getEstado() == null || reporte.getEstado() == EstadoReporte.PENDIENTE) {
            reporte.setEstado(EstadoReporte.EN_ATENCION);
            if (reporte.getFechaAtencion() == null) {
                reporte.setFechaAtencion(LocalDateTime.now());
            }
            reporteRepository.save(reporte);
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
        notificacion.put("reporteEstado", EstadoReporte.EN_ATENCION.name());
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
    public Map<String, Object> disponibilizarUnidad(
            Long unidadId,
            String operadorEmail,
            String operador,
            String personal,
            String novedades) {
        UnidadBomberil unidad = unidadRepository.findWithLockById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + unidadId));
        if (unidad.getOperadorEmail() == null
                || operadorEmail == null
                || !operadorEmail.equalsIgnoreCase(unidad.getOperadorEmail())) {
            throw new RuntimeException("Solo el operador vinculado puede finalizar esta emergencia.");
        }

        ReporteCiudadano reporteAnterior = unidad.getReporteAsignado();
        if (reporteAnterior == null
                || (unidad.getEstado() != EstadoUnidad.EN_RUTA && unidad.getEstado() != EstadoUnidad.EN_SITIO)) {
            throw new RuntimeException("La unidad no tiene una emergencia activa para finalizar.");
        }
        validarReporteFinal(personal, novedades);

        Long reporteId = reporteAnterior.getId();
        // Serializa el cierre de todas las unidades del mismo reporte. Así, si
        // dos operadores terminan casi simultáneamente, exactamente uno detecta
        // que fue la última unidad y persiste el cierre del incidente.
        reporteAnterior = reporteRepository.findWithLockById(reporteId)
                .orElseThrow(() -> new RuntimeException("El reporte asignado ya no existe."));
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
                reporteAnterior.setEstado(EstadoReporte.ATENDIDO);
                reporteAnterior.setFechaCierre(LocalDateTime.now());
                reporteRepository.save(reporteAnterior);
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
        notificacion.put("reporteEstado",
                reporteCerrado ? EstadoReporte.ATENDIDO.name() : EstadoReporte.EN_ATENCION.name());

        log.info("--- DIFUNDIENDO LIBERACION VIA WEBSOCKET: {} ---", notificacion);
        messagingTemplate.convertAndSend("/topic/unidades-estado", (Object) notificacion);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("unidadId", unidadId);
        respuesta.put("nombre", unidad.getNombre());
        respuesta.put("estadoNuevo", EstadoUnidad.DISPONIBLE.name());
        respuesta.put("reporteAnteriorId", reporteId);
        respuesta.put("reporteCerrado", reporteCerrado);
        respuesta.put("reporteEstado",
                reporteCerrado ? EstadoReporte.ATENDIDO.name() : EstadoReporte.EN_ATENCION.name());
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
    public Map<String, Object> marcarEnSitio(Long unidadId, String operadorEmail) {
        UnidadBomberil unidad = unidadRepository.findWithLockById(unidadId)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada: " + unidadId));
        if (unidad.getOperadorEmail() == null
                || operadorEmail == null
                || !operadorEmail.equalsIgnoreCase(unidad.getOperadorEmail())) {
            throw new RuntimeException("Solo el operador vinculado puede confirmar la llegada.");
        }
        if (unidad.getEstado() != EstadoUnidad.EN_RUTA || unidad.getReporteAsignado() == null) {
            throw new RuntimeException("La unidad no está en ruta hacia una emergencia activa.");
        }

        unidad.setEstado(EstadoUnidad.EN_SITIO);
        unidadRepository.save(unidad);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", unidad.getId());
        payload.put("nombre", unidad.getNombre());
        payload.put("estado", EstadoUnidad.EN_SITIO.name());
        payload.put("tipo", "LLEGADA_SITIO");
        payload.put("reporteId", unidad.getReporteAsignado().getId());

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
        if (unidad == null
                || unidad.getNombre() == null
                || !unidad.getNombre().trim().matches("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9' .-]{2,50}")) {
            throw new RuntimeException("Ingresa un nombre de unidad válido de 2 a 50 caracteres.");
        }
        if (unidad.getTipo() == null
                || !unidad.getTipo().trim().matches("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9' /.-]{2,80}")) {
            throw new RuntimeException("Ingresa un tipo de unidad válido de 2 a 80 caracteres.");
        }
        unidad.setNombre(unidad.getNombre().trim());
        unidad.setTipo(unidad.getTipo().trim());
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
        UnidadBomberil unidad = unidadRepository.findWithLockById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + id));
        if (unidad.getReporteAsignado() != null || unidad.getEstado() != EstadoUnidad.DISPONIBLE) {
            throw new RuntimeException("No se puede eliminar una unidad durante una emergencia activa.");
        }
        unidadRepository.delete(unidad);
        difundirActualizacionGeneral();
    }

    public List<BitacoraUnidad> obtenerBitacoras() {
        return bitacoraRepository.findAllByOrderByFechaHoraDesc();
    }

    /**
     * Vista segura y sin relaciones JPA circulares para el tablero central.
     */
    public List<Map<String, Object>> obtenerEstadoOperativo() {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(SEGUNDOS_PRESENCIA_OPERADOR);
        return unidadRepository.findAll().stream().map(unidad -> {
            Map<String, Object> estado = new HashMap<>();
            estado.put("id", unidad.getId());
            estado.put("nombre", unidad.getNombre());
            estado.put("tipo", unidad.getTipo());
            estado.put("estado", unidad.getEstado().name());
            estado.put("operador", unidad.getOperadorNombre() != null ? unidad.getOperadorNombre() : "");
            estado.put("operadorConectado",
                    unidad.getOperadorUltimoHeartbeat() != null
                            && unidad.getOperadorUltimoHeartbeat().isAfter(limite));
            estado.put("reporteId",
                    unidad.getReporteAsignado() != null ? unidad.getReporteAsignado().getId() : null);
            return estado;
        }).toList();
    }

    /**
     * Cambiar manualmente el estado de una unidad (Forzado desde Central)
     */
    @Transactional
    public UnidadBomberil cambiarEstadoManual(Long id, EstadoUnidad nuevoEstado) {
        UnidadBomberil unidad = unidadRepository.findWithLockById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada con ID: " + id));

        if (nuevoEstado == null) {
            throw new RuntimeException("Selecciona un estado válido.");
        }
        if (nuevoEstado == EstadoUnidad.DISPONIBLE && unidad.getReporteAsignado() != null) {
            throw new RuntimeException(
                    "La unidad tiene una emergencia activa. El operador debe finalizarla y registrar su bitácora.");
        }
        if (nuevoEstado != EstadoUnidad.DISPONIBLE && unidad.getReporteAsignado() == null) {
            throw new RuntimeException(
                    "No se puede poner una unidad en ruta o en sitio sin un reporte asignado.");
        }

        unidad.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoUnidad.DISPONIBLE) {
            unidad.setReporteAsignado(null);
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

    private void validarReporteFinal(String personal, String novedades) {
        String personalNormalizado = personal != null ? personal.trim() : "";
        String novedadesNormalizadas = novedades != null ? novedades.trim() : "";
        if (personalNormalizado.length() < 3 || personalNormalizado.length() > 500) {
            throw new RuntimeException("El personal involucrado debe contener entre 3 y 500 caracteres.");
        }
        if (novedadesNormalizadas.length() < 10 || novedadesNormalizadas.length() > 5000) {
            throw new RuntimeException("Las novedades deben contener entre 10 y 5000 caracteres.");
        }
    }
}
