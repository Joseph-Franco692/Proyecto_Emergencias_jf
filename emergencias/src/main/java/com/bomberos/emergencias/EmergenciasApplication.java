package com.bomberos.emergencias;

import com.bomberos.emergencias.services.UnidadBomberilService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EmergenciasApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmergenciasApplication.class, args);
	}

	@Bean
	CommandLineRunner seedUnidades(UnidadBomberilService unidadService) {
		return args -> unidadService.inicializarUnidadesPredeterminadas();
	}
}
