package com.bomberos.emergencias.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CORS configurado desde este mismo bean
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 2. CSRF desactivado (API REST stateless)
            .csrf(AbstractHttpConfigurer::disable)
            // 3. Autorización por endpoint
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",  // registro, login-google, verify, session, logout
                    "/api/mfa/**",   // login manual, setup 2fa, confirm, verify
                    "/api/health",
                    "/ws/**",
                    "/api/reportes/ciudadano"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // 4. Sesión stateless — usamos JWT, no cookies de sesión
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 5. Proveedor de autenticación + filtro JWT
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Orígenes permitidos — Angular dev
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200",
            "http://localhost:4201",
            "http://127.0.0.1:4200"
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Cabeceras permitidas — incluye Authorization para JWT
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

        // NO credenciales — Angular usa Authorization header, no cookies
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
