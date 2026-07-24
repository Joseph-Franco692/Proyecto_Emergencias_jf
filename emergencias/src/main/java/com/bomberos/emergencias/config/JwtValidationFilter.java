package com.bomberos.emergencias.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Este filtro fue reemplazado por el nuevo JwtAuthenticationFilter de Spring Security.
 * Se mantiene aquí pero sin lógica para no interferir con el nuevo sistema de autenticación.
 */
@Component
public class JwtValidationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Filtro desactivado — la validación JWT ahora la maneja JwtAuthenticationFilter
        // en el paquete security/, integrado con Spring Security.
        chain.doFilter(request, response);
    }
}
