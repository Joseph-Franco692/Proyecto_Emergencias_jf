package com.bomberos.emergencias;

import com.bomberos.emergencias.repositories.UsuarioRepository;
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

	@Bean
	CommandLineRunner initRoles(UsuarioRepository usuarioRepository) {
		return args -> {
			usuarioRepository.findAll().forEach(user -> {
				boolean updated = false;
				if ("jafranco5@espe.edu.ec".equalsIgnoreCase(user.getEmail())) {
					if (!"ADMIN".equals(user.getRole())) {
						user.setRole("ADMIN");
						updated = true;
					}
					if (user.getZoneCode() == null || user.getZoneCode().isBlank()) {
						user.setZoneCode("ZONA-SDMC-2026");
						updated = true;
					}
				} else {
					if (user.getRole() == null || user.getRole().isBlank()) {
						user.setRole("OPERADOR");
						updated = true;
					}
				}
				if (updated) {
					usuarioRepository.save(user);
					System.out.printf("[ROLE-INIT] Rol/Zona de %s establecido a %s / %s%n", user.getEmail(), user.getRole(), user.getZoneCode());
				}
			});
		};
	}
}
