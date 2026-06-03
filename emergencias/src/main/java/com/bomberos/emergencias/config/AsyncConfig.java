package com.bomberos.emergencias.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor") // <-- Definimos explícitamente el nombre que Spring está pidiendo
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);       // Hilos mínimos activos de forma permanente
        executor.setMaxPoolSize(8);        // Hilos máximos si el servidor se satura de fotos
        executor.setQueueCapacity(500);    // Cuántas imágenes puede dejar en cola de espera
        executor.setThreadNamePrefix("BomberosAsync-"); // Prefijo para identificar el hilo en consola
        executor.initialize();
        return executor;
    }
}