package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.services.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.registerManual(
                request.get("name"),
                request.get("email"),
                request.get("password")
        ));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<Map<String, Object>> resendCode(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.resendCode(
                request.get("email")
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.verifyAccount(
                request.get("email"),
                request.get("code")
        ));
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> loginGoogle(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.loginWithGoogle(
                request.get("credential")
        ));
    }

    @PostMapping("/link-zone")
    public ResponseEntity<Map<String, Object>> linkZone(@RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                email = auth.getName();
            }
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "El correo es requerido para la vinculación de zona."));
        }
        return ResponseEntity.ok(authService.linkUserToZone(email, request.get("zoneCode")));
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> getSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(authService.getSessionUser(auth.getName()));
        }
        return ResponseEntity.status(401).body(Map.of("success", false, "message", "No hay sesión activa"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok(Map.of("success", true, "message", "Sesión cerrada correctamente"));
    }
}
