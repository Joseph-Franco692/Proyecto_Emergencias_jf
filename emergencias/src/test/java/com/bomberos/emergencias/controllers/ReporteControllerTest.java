package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.services.UnidadBomberilService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {

    @Mock
    private UnidadBomberilService unidadService;

    @InjectMocks
    private ReporteController controller;

    @Test
    void administradorPuedeDespachar() {
        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin@bomberos.local",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        List<Long> unidades = List.of(2L);
        when(unidadService.despacharUnidades(10L, unidades))
                .thenReturn(Map.of("mensaje", "ok"));

        ResponseEntity<Map<String, Object>> respuesta =
                controller.despacharUnidades(10L, unidades, admin);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        verify(unidadService).despacharUnidades(10L, unidades);
    }

    @Test
    void operadorNoPuedeDespachar() {
        Authentication operador = new UsernamePasswordAuthenticationToken(
                "operador@bomberos.local",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))
        );

        ResponseEntity<Map<String, Object>> respuesta =
                controller.despacharUnidades(10L, List.of(2L), operador);

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
        verifyNoInteractions(unidadService);
    }
}
