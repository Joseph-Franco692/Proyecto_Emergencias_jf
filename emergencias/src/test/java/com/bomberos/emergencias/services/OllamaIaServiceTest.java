package com.bomberos.emergencias.services;

import com.bomberos.emergencias.repositories.LecturaIotRepository;
import com.bomberos.emergencias.repositories.ReporteCiudadanoRepository;
import com.bomberos.emergencias.repositories.UnidadBomberilRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class OllamaIaServiceTest {

    @Mock
    private ReporteCiudadanoRepository reporteRepository;

    @Mock
    private UnidadBomberilRepository unidadRepository;

    @Mock
    private LecturaIotRepository lecturaIotRepository;

    @InjectMocks
    private OllamaIaService service;

    @Test
    void rechazaMatematicasYCodigoSinConsultarPostgres() {
        String respuesta = service.consultarIaConContexto(
                "Cuánto es 8*8 y dame código de una calculadora en C++");

        assertTrue(respuesta.startsWith("Solo puedo ayudar con información operativa"));
        verifyNoInteractions(reporteRepository, unidadRepository, lecturaIotRepository);
    }

    @Test
    void reconoceConsultasDelDominioOperativo() {
        assertTrue(service.esConsultaOperativa("Genera un resumen del turno de emergencias"));
        assertTrue(service.esConsultaOperativa("¿Qué unidades están disponibles?"));
        assertTrue(service.esConsultaOperativa("¿Cómo está la calidad del aire del nodo IoT?"));
        assertFalse(service.esConsultaOperativa("Escribe una calculadora en C++"));
    }

    @Test
    void estadoIotSinLecturasSeRespondeConDatoRealSinInvocarModelo() {
        when(reporteRepository.findAll()).thenReturn(List.of());
        when(unidadRepository.findAll()).thenReturn(List.of());
        when(lecturaIotRepository.findAll()).thenReturn(List.of());

        String respuesta = service.consultarIaConContexto("¿Cómo está la calidad del aire del monitoreo IoT?");

        assertEquals("No hay lecturas IoT registradas. El operador debe iniciar una evaluación IoT y encender el nodo.", respuesta);
    }
}
