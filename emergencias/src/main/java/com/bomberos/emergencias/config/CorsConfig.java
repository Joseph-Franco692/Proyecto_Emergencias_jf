package com.bomberos.emergencias.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS ahora lo maneja SecurityConfig (Spring Security CorsConfigurationSource).
 * Este archivo solo registra el handler de uploads estáticos.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}