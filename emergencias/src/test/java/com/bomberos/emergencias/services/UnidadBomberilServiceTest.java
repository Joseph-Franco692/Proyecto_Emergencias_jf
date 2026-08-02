package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.EstadoUnidad;
import com.bomberos.emergencias.models.EstadoReporte;
import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.models.UnidadBomberil;
import com.bomberos.emergencias.models.BitacoraUnidad;
import com.bomberos.emergencias.repositories.BitacoraUnidadRepository;
import com.bomberos.emergencias.repositories.LecturaIotRepository;
import com.bomberos.emergencias.repositories.ReporteCiudadanoRepository;
import com.bomberos.emergencias.repositories.SesionIotRepository;
import com.bomberos.emergencias.repositories.UnidadBomberilRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UnidadBomberilServiceTest {

    @Mock
    private UnidadBomberilRepository unidadRepository;
    @Mock
    private BitacoraUnidadRepository bitacoraRepository;
    @Mock
    private ReporteCiudadanoRepository reporteRepository;
    @Mock
    private SesionIotRepository sesionIotRepository;
    @Mock
    private LecturaIotRepository lecturaIotRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private UnidadBomberilService service;

    @Test
    void despachoGuardaAsignacionYPublicaCoordenadas() {
        ReporteCiudadano reporte = new ReporteCiudadano();
        reporte.setId(15L);
        reporte.setDescripcion("Incendio estructural");
        reporte.setLatitud(new BigDecimal("-0.253012"));
        reporte.setLongitud(new BigDecimal("-79.177024"));

        UnidadBomberil unidad = new UnidadBomberil();
        unidad.setId(3L);
        unidad.setNombre("B-01 Autobomba");
        unidad.setTipo("Ataque contra incendios");
        unidad.setEstado(EstadoUnidad.DISPONIBLE);
        unidad.setOperadorEmail("operador@bomberos.local");
        unidad.setOperadorNombre("Operador de turno");
        unidad.setOperadorUltimoHeartbeat(LocalDateTime.now());

        when(reporteRepository.findWithLockById(15L)).thenReturn(Optional.of(reporte));
        when(unidadRepository.findWithLockById(3L)).thenReturn(Optional.of(unidad));

        service.despacharUnidades(15L, List.of(3L));

        assertEquals(EstadoUnidad.EN_RUTA, unidad.getEstado());
        assertEquals(EstadoReporte.EN_ATENCION, reporte.getEstado());
        assertSame(reporte, unidad.getReporteAsignado());
        verify(unidadRepository).save(unidad);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/unidades-estado"), evento.capture());
        Map<String, Object> payload = (Map<String, Object>) evento.getValue();
        assertEquals("DESPACHO", payload.get("tipo"));
        assertEquals(15L, payload.get("reporteId"));
        assertEquals(reporte.getLatitud(), payload.get("latitud"));
        assertEquals(reporte.getLongitud(), payload.get("longitud"));
    }

    @Test
    void reporteAtendidoNoPuedeVolverADespacharse() {
        ReporteCiudadano reporte = new ReporteCiudadano();
        reporte.setId(20L);
        reporte.setEstado(EstadoReporte.ATENDIDO);
        when(reporteRepository.findWithLockById(20L)).thenReturn(Optional.of(reporte));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.despacharUnidades(20L, List.of(3L)));

        assertEquals("El reporte ya fue atendido y no puede volver a ser asignado.", error.getMessage());
        verify(unidadRepository, never()).findWithLockById(3L);
    }

    @Test
    void ultimaUnidadFinalizaReporteYPublicaCierre() {
        ReporteCiudadano reporte = new ReporteCiudadano();
        reporte.setId(25L);
        reporte.setEstado(EstadoReporte.EN_ATENCION);

        UnidadBomberil unidad = new UnidadBomberil();
        unidad.setId(7L);
        unidad.setNombre("R-07 Rescate");
        unidad.setTipo("Rescate");
        unidad.setEstado(EstadoUnidad.EN_SITIO);
        unidad.setOperadorEmail("operador@bomberos.local");
        unidad.setReporteAsignado(reporte);

        when(unidadRepository.findWithLockById(7L)).thenReturn(Optional.of(unidad));
        when(reporteRepository.findWithLockById(25L)).thenReturn(Optional.of(reporte));
        when(unidadRepository.findByReporteAsignadoId(25L)).thenReturn(List.of());
        when(sesionIotRepository.findFirstByReporteIdOrderByFechaInicioDesc(25L))
                .thenReturn(Optional.empty());
        when(sesionIotRepository.findFirstByReporteIdAndEstadoOrderByFechaInicioDesc(25L, "ACTIVA"))
                .thenReturn(Optional.empty());
        when(bitacoraRepository.save(org.mockito.ArgumentMatchers.any(BitacoraUnidad.class)))
                .thenAnswer(invocation -> {
                    BitacoraUnidad bitacora = invocation.getArgument(0);
                    bitacora.setId(1L);
                    return bitacora;
                });

        Map<String, Object> respuesta = service.disponibilizarUnidad(
                7L,
                "operador@bomberos.local",
                "Operador",
                "Equipo de rescate",
                "Sin novedades");

        assertEquals(EstadoReporte.ATENDIDO, reporte.getEstado());
        assertEquals(EstadoUnidad.DISPONIBLE, unidad.getEstado());
        assertEquals(true, respuesta.get("reporteCerrado"));
        assertEquals("ATENDIDO", respuesta.get("reporteEstado"));
        verify(reporteRepository).save(reporte);
    }

    @Test
    void finalizarExigeBitacoraValidaEnElServidor() {
        ReporteCiudadano reporte = new ReporteCiudadano();
        reporte.setId(30L);
        reporte.setEstado(EstadoReporte.EN_ATENCION);

        UnidadBomberil unidad = new UnidadBomberil();
        unidad.setId(8L);
        unidad.setEstado(EstadoUnidad.EN_SITIO);
        unidad.setOperadorEmail("operador@bomberos.local");
        unidad.setReporteAsignado(reporte);

        when(unidadRepository.findWithLockById(8L)).thenReturn(Optional.of(unidad));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.disponibilizarUnidad(
                        8L,
                        "operador@bomberos.local",
                        "Operador",
                        "x",
                        "corto"));

        assertEquals("El personal involucrado debe contener entre 3 y 500 caracteres.", error.getMessage());
        verify(bitacoraRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(reporteRepository, never()).save(reporte);
    }

    @Test
    void administradorNoPuedeLiberarManualmenteUnaUnidadActiva() {
        ReporteCiudadano reporte = new ReporteCiudadano();
        reporte.setId(31L);

        UnidadBomberil unidad = new UnidadBomberil();
        unidad.setId(9L);
        unidad.setEstado(EstadoUnidad.EN_RUTA);
        unidad.setReporteAsignado(reporte);

        when(unidadRepository.findWithLockById(9L)).thenReturn(Optional.of(unidad));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.cambiarEstadoManual(9L, EstadoUnidad.DISPONIBLE));

        assertEquals(
                "La unidad tiene una emergencia activa. El operador debe finalizarla y registrar su bitácora.",
                error.getMessage());
        verify(unidadRepository, never()).save(unidad);
    }

    @Test
    void noPermiteEliminarUnaUnidadEnEmergencia() {
        UnidadBomberil unidad = new UnidadBomberil();
        unidad.setId(10L);
        unidad.setEstado(EstadoUnidad.EN_SITIO);
        unidad.setReporteAsignado(new ReporteCiudadano());

        when(unidadRepository.findWithLockById(10L)).thenReturn(Optional.of(unidad));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.eliminarUnidad(10L));

        assertEquals("No se puede eliminar una unidad durante una emergencia activa.", error.getMessage());
        verify(unidadRepository, never()).delete(unidad);
    }
}
