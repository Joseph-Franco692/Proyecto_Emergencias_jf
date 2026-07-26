package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.models.Usuario;
import com.bomberos.emergencias.repositories.UsuarioRepository;
import com.bomberos.emergencias.services.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;

    /**
     * Listar todos los usuarios para la gestión de permisos.
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        checkAdminPermission();

        List<Map<String, Object>> users = usuarioRepository.findAll().stream()
                .map(authService::buildUserDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    /**
     * Cambiar el rol de un usuario (ADMIN u OPERADOR).
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request
    ) {
        checkAdminPermission();

        String newRole = request.get("role");
        if (newRole == null || (!"ADMIN".equals(newRole) && !"OPERADOR".equals(newRole))) {
            throw new RuntimeException("El rol debe ser 'ADMIN' u 'OPERADOR'.");
        }

        Usuario user = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        user.setRole(newRole);
        usuarioRepository.save(user);

        System.out.printf("[ADMIN] Rol de %s actualizado a %s%n", user.getEmail(), newRole);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Rol actualizado correctamente a " + newRole,
                "user", authService.buildUserDto(user)
        ));
    }

    private void checkAdminPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("No autenticado.");
        }
        Usuario user = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Acceso denegado: Se requieren permisos de Administrador.");
        }
    }
}
