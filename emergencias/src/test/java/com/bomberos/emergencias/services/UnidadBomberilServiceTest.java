package com.bomberos.emergencias.services;

import com.bomberos.emergencias.models.EstadoUnidad;
import com.bomberos.emergencias.models.ReporteCiudadano;
import com.bomberos.emergencias.models.UnidadBomberil;
import com.bomberos.emergencias.repositories.BitacoraUnidadRepository;
import com.bomberos.emergencias.repositories.ReporteCiudadanoRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnidadBomberilServiceTest {

    @Mock
    private UnidadBomberilRepository unidadRepository;
    @Mock
    private BitacoraUnidadRepository bitacoraRepository;
    @Mock
    private ReporteCiudadanoRepository reporteRepository;
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

        when(reporteRepository.findById(15L)).thenReturn(Optional.of(reporte));
        when(unidadRepository.findById(3L)).thenReturn(Optional.of(unidad));

        service.despacharUnidades(15L, List.of(3L));

        assertEquals(EstadoUnidad.EN_RUTA, unidad.getEstado());
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
}
