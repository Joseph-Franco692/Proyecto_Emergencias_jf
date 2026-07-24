package com.bomberos.emergencias.services.auth;

import com.bomberos.emergencias.models.Usuario;
import com.bomberos.emergencias.repositories.UsuarioRepository;
import com.bomberos.emergencias.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    private static final String GOOGLE_CLIENT_ID =
        "972842219867-4t1bv2l523jevau1uqjrforlfoj51hbg.apps.googleusercontent.com";

    // ─── REGISTRO MANUAL ──────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> registerManual(String name, String email, String password) {
        // Validaciones básicas
        if (name == null || name.isBlank())     throw new RuntimeException("El nombre es requerido.");
        if (email == null || email.isBlank())   throw new RuntimeException("El correo es requerido.");
        if (password == null || password.isBlank()) throw new RuntimeException("La contraseña es requerida.");
        if (password.length() < 6)              throw new RuntimeException("La contraseña debe tener al menos 6 caracteres.");

        Optional<Usuario> existingOpt = repository.findByEmail(email.toLowerCase().trim());

        if (existingOpt.isPresent()) {
            Usuario existing = existingOpt.get();

            if ("ACTIVE".equals(existing.getStatus())) {
                // Cuenta activa con 2FA configurado (oauth o manual ya verificado)
                throw new RuntimeException("El correo ya tiene una cuenta activa. Inicia sesión directamente.");
            }

            // Cuenta INACTIVE — reenviar código con datos actualizados
            String code = generateCode();
            existing.setName(name.trim());
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setVerificationCode(code);
            existing.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            existing.setVerificationAttempts(0);
            existing.setUpdatedAt(LocalDateTime.now());
            repository.save(existing);

            sendVerificationEmail(email.toLowerCase().trim(), name.trim(), code);

            return Map.of(
                "success", true,
                "message", "Ya existía un registro pendiente. Nuevo código enviado a tu correo.",
                "email", email.toLowerCase().trim()
            );
        }

        // Usuario completamente nuevo
        String code = generateCode();

        Usuario user = new Usuario();
        user.setName(name.trim());
        user.setEmail(email.toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("INACTIVE");
        user.setVerificationCode(code);
        user.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        user.setVerificationAttempts(0);
        repository.save(user);

        sendVerificationEmail(email.toLowerCase().trim(), name.trim(), code);

        return Map.of(
            "success", true,
            "message", "Cuenta creada. Revisa tu correo para obtener el código de verificación (válido 10 min).",
            "email", email.toLowerCase().trim()
        );
    }

    // ─── REENVIAR CÓDIGO ──────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> resendCode(String email) {
        Usuario user = repository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("No existe una cuenta registrada con ese correo."));

        if ("ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("La cuenta ya está activa. Puedes iniciar sesión.");
        }

        String code = generateCode();
        user.setVerificationCode(code);
        user.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        user.setVerificationAttempts(0);
        user.setUpdatedAt(LocalDateTime.now());
        repository.save(user);

        sendVerificationEmail(email, user.getName(), code);

        return Map.of(
            "success", true,
            "message", "Nuevo código de verificación enviado a tu correo."
        );
    }

    // ─── VERIFICAR CUENTA ─────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> verifyAccount(String email, String code) {
        if (email == null || code == null) throw new RuntimeException("Correo y código son requeridos.");

        Usuario user = repository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("No existe una cuenta con ese correo."));

        if ("ACTIVE".equals(user.getStatus())) {
            return Map.of(
                "success", true,
                "message", "La cuenta ya estaba activa. Puedes iniciar sesión."
            );
        }

        if (user.getVerificationCode() == null) {
            throw new RuntimeException("No hay un código pendiente. Solicita uno nuevo.");
        }

        if (user.getExpiresAt() != null && user.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El código expiró. Haz clic en 'Reenviar código' para obtener uno nuevo.");
        }

        // Limitar intentos fallidos
        if (user.getVerificationAttempts() >= 5) {
            throw new RuntimeException("Demasiados intentos fallidos. Solicita un código nuevo.");
        }

        if (!user.getVerificationCode().equals(code.trim())) {
            user.setVerificationAttempts(user.getVerificationAttempts() + 1);
            repository.save(user);
            int remaining = 5 - user.getVerificationAttempts();
            throw new RuntimeException("Código incorrecto. Te quedan " + remaining + " intentos.");
        }

        // Activar cuenta
        user.setStatus("ACTIVE");
        user.setVerificationCode(null);
        user.setExpiresAt(null);
        user.setVerificationAttempts(0);
        user.setActivatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        repository.save(user);

        return Map.of(
            "success", true,
            "message", "¡Cuenta activada! Ahora inicia sesión con tu correo y contraseña."
        );
    }

    // ─── LOGIN MANUAL (PRIMER PASO) ───────────────────────────────────────────────
    public Map<String, Object> loginManualFirstStep(String email, String password) {
        if (email == null || password == null) throw new RuntimeException("Correo y contraseña son requeridos.");

        Usuario user = repository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("Correo o contraseña incorrectos."));

        if ("INACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Cuenta no verificada. Revisa tu correo o solicita un nuevo código.");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Cuenta inactiva o suspendida. Contacta al administrador.");
        }

        // Verificar contraseña mediante Spring Security
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email.toLowerCase().trim(), password)
            );
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Correo o contraseña incorrectos.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("user", buildUserDto(user));

        if (user.isMfaEnabled()) {
            // Ya tiene 2FA — pedir código del Authenticator
            response.put("requiresMfa", true);
            response.put("requiresMfaSetup", false);
            response.put("message", "Ingresa el código de 6 dígitos de tu Authenticator.");
        } else {
            // Primera vez — solicitar setup del QR
            response.put("requiresMfa", false);
            response.put("requiresMfaSetup", true);
            response.put("message", "Configura la autenticación en dos pasos (2FA) para continuar.");
        }

        return response;
    }

    // ─── LOGIN CON GOOGLE ─────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> loginWithGoogle(String credential) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), new GsonFactory()
            )
                .setAudience(Collections.singletonList(GOOGLE_CLIENT_ID))
                .build();

            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new RuntimeException("Token de Google inválido o expirado. Intenta de nuevo.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email   = payload.getEmail().toLowerCase().trim();
            String name    = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            if (name == null || name.isBlank()) {
                name = email.split("@")[0];
            }

            Usuario user = repository.findByEmail(email).orElse(null);

            if (user == null) {
                // Registro automático con Google
                user = new Usuario();
                user.setEmail(email);
                user.setName(name);
                user.setStatus("ACTIVE");
                user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                user = repository.save(user);
                System.out.println("[GOOGLE-AUTH] Nuevo usuario registrado: " + email);
            } else {
                // Actualizar nombre/foto y activar si estaba INACTIVE
                if (!name.equals(user.getName())) user.setName(name);
                if (!"ACTIVE".equals(user.getStatus())) user.setStatus("ACTIVE");
                user.setUpdatedAt(LocalDateTime.now());
                user = repository.save(user);
                System.out.println("[GOOGLE-AUTH] Usuario autenticado: " + email);
            }

            String token = jwtService.generateToken(user);

            Map<String, Object> userDto = buildUserDto(user);
            if (picture != null) userDto.put("picture", picture);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("user", userDto);
            response.put("message", "Inicio de sesión con Google exitoso.");
            return response;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error al validar token de Google: " + e.getMessage(), e);
        }
    }

    // ─── OBTENER SESIÓN ───────────────────────────────────────────────────────────
    public Map<String, Object> getSessionUser(String email) {
        Usuario user = repository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario de sesión no encontrado."));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", buildUserDto(user));
        return response;
    }

    // ─── HELPERS PRIVADOS ─────────────────────────────────────────────────────────
    private String generateCode() {
        // Siempre 6 dígitos (con ceros a la izquierda si hace falta)
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private void sendVerificationEmail(String email, String name, String code) {
        try {
            emailService.sendVerificationEmail(email, name, code);
            System.out.printf("[AUTH] Correo enviado a %s | código: %s%n", email, code);
        } catch (Exception e) {
            // El correo falló pero el usuario ya está en BD — mostramos el código en consola
            System.err.printf("[AUTH] No se pudo enviar correo a %s: %s%n", email, e.getMessage());
            System.out.printf("[AUTH] *** CÓDIGO PARA PRUEBAS (consola): %s ***%n", code);
        }
    }

    public Map<String, Object> buildUserDto(Usuario user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",         user.getId().toString());
        dto.put("name",       user.getName());
        dto.put("email",      user.getEmail());
        dto.put("status",     user.getStatus());
        dto.put("mfaEnabled", user.isMfaEnabled());
        dto.put("picture",    "");
        return dto;
    }
}
