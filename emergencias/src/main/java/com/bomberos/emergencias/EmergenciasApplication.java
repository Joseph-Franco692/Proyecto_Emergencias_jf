package com.bomberos.emergencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EmergenciasApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmergenciasApplication.class, args);
	}


}
