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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    private static final int RESET_TOKEN_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String GOOGLE_CLIENT_ID =
        "972842219867-4t1bv2l523jevau1uqjrforlfoj51hbg.apps.googleusercontent.com";

    // ─── REGISTRO MANUAL ──────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> registerManual(String name, String email, String password) {
        if (name == null || name.isBlank())     throw new RuntimeException("El nombre es requerido.");
        if (email == null || email.isBlank())   throw new RuntimeException("El correo es requerido.");
        if (password == null || password.isBlank()) throw new RuntimeException("La contraseña es requerida.");
        if (password.length() < 6)              throw new RuntimeException("La contraseña debe tener al menos 6 caracteres.");

        String cleanEmail = email.toLowerCase().trim();
        boolean isAdminEmail = "jafranco5@espe.edu.ec".equalsIgnoreCase(cleanEmail);
        String role = isAdminEmail ? "ADMIN" : "OPERADOR";
        String zoneCode = isAdminEmail ? "ZONA-SDMC-2026" : null;

        Optional<Usuario> existingOpt = repository.findByEmail(cleanEmail);

        if (existingOpt.isPresent()) {
            Usuario existing = existingOpt.get();

            if ("ACTIVE".equals(existing.getStatus())) {
                throw new RuntimeException("El correo ya tiene una cuenta activa. Inicia sesión directamente.");
            }

            String code = generateCode();
            existing.setName(name.trim());
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setRole(role);
            if (isAdminEmail) existing.setZoneCode(zoneCode);
            existing.setVerificationCode(code);
            existing.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            existing.setVerificationAttempts(0);
            existing.setUpdatedAt(LocalDateTime.now());
            repository.save(existing);

            sendVerificationEmail(cleanEmail, name.trim(), code);

            return Map.of(
                "success", true,
                "message", "Ya existía un registro pendiente. Nuevo código enviado a tu correo.",
                "email", cleanEmail
            );
        }

        String code = generateCode();

        Usuario user = new Usuario();
        user.setName(name.trim());
        user.setEmail(cleanEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("INACTIVE");
        user.setRole(role);
        if (isAdminEmail) user.setZoneCode(zoneCode);
        user.setVerificationCode(code);
        user.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        user.setVerificationAttempts(0);
        repository.save(user);

        sendVerificationEmail(cleanEmail, name.trim(), code);

        return Map.of(
            "success", true,
            "message", "Cuenta creada. Revisa tu correo para obtener el código de verificación (válido 10 min).",
            "email", cleanEmail
        );
    }

    // ─── REENVIAR CÓDIGO ──────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> resendCode(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("El correo es requerido.");
        }

        String cleanEmail = email.toLowerCase().trim();
        Usuario user = repository.findWithLockByEmail(cleanEmail)
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

        sendVerificationEmail(cleanEmail, user.getName(), code);

        return Map.of(
            "success", true,
            "message", "Nuevo código enviado. Utiliza únicamente el correo más reciente."
        );
    }

    // ─── VERIFICAR CUENTA ─────────────────────────────────────────────────────────
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public Map<String, Object> verifyAccount(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new RuntimeException("Correo y código son requeridos.");
        }

        String cleanEmail = email.toLowerCase().trim();
        String cleanCode = code.replaceAll("\\D", "");
        if (cleanCode.length() != 6) {
            throw new RuntimeException("El código debe contener exactamente 6 dígitos.");
        }

        Usuario user = repository.findWithLockByEmail(cleanEmail)
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

        if (user.getVerificationAttempts() >= 5) {
            throw new RuntimeException("Demasiados intentos fallidos. Solicita un código nuevo.");
        }

        if (!user.getVerificationCode().equals(cleanCode)) {
            user.setVerificationAttempts(user.getVerificationAttempts() + 1);
            repository.save(user);
            int remaining = 5 - user.getVerificationAttempts();
            throw new InvalidVerificationCodeException(
                    "Código incorrecto o reemplazado por uno más reciente. Te quedan "
                            + remaining + " intentos."
            );
        }

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

    // ─── RECUPERACIÓN DE CONTRASEÑA ──────────────────────────────────────────
    /**
     * Genera un token de un solo uso. La respuesta es siempre genérica para no
     * revelar si una dirección de correo está registrada.
     */
    @Transactional
    public Map<String, Object> requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("El correo es requerido.");
        }

        String cleanEmail = email.toLowerCase().trim();
        repository.findByEmail(cleanEmail)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .ifPresent(user -> {
                    String rawToken = generateSecureResetToken();
                    user.setResetToken(hashResetToken(rawToken));
                    user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES));
                    user.setUpdatedAt(LocalDateTime.now());
                    repository.save(user);

                    String baseUrl = frontendUrl.replaceAll("/+$", "");
                    String resetUrl = baseUrl + "/login?resetToken=" + rawToken;
                    emailService.sendPasswordResetEmail(
                            user.getEmail(),
                            user.getName(),
                            resetUrl,
                            RESET_TOKEN_MINUTES
                    );
                });

        return Map.of(
                "success", true,
                "message", "Si el correo corresponde a una cuenta activa, recibirás un enlace para restablecer tu contraseña."
        );
    }

    /**
     * Consume el token una sola vez y reemplaza la contraseña mediante BCrypt.
     */
    @Transactional
    public Map<String, Object> resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RuntimeException("El enlace de recuperación no es válido.");
        }
        validateNewPassword(newPassword);

        Usuario user = repository.findWithLockByResetToken(hashResetToken(rawToken))
                .orElseThrow(() -> new RuntimeException("El enlace de recuperación no es válido o ya fue utilizado."));

        if (user.getResetTokenExpiresAt() == null
                || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            user.setResetToken(null);
            user.setResetTokenExpiresAt(null);
            repository.save(user);
            throw new RuntimeException("El enlace de recuperación expiró. Solicita uno nuevo.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        user.setUpdatedAt(LocalDateTime.now());
        repository.save(user);

        return Map.of(
                "success", true,
                "message", "Contraseña actualizada correctamente. Ya puedes iniciar sesión."
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
            response.put("requiresMfa", true);
            response.put("requiresMfaSetup", false);
            response.put("message", "Ingresa el código de 6 dígitos de tu Authenticator.");
        } else {
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

            boolean isAdminEmail = "jafranco5@espe.edu.ec".equalsIgnoreCase(email);
            String role = isAdminEmail ? "ADMIN" : "OPERADOR";
            Usuario user = repository.findByEmail(email).orElse(null);

            if (user == null) {
                user = new Usuario();
                user.setEmail(email);
                user.setName(name);
                user.setStatus("ACTIVE");
                user.setRole(role);
                if (isAdminEmail) user.setZoneCode("ZONA-SDMC-2026");
                user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                user = repository.save(user);
                System.out.println("[GOOGLE-AUTH] Nuevo usuario registrado (" + role + "): " + email);
            } else {
                if (!name.equals(user.getName())) user.setName(name);
                if (!"ACTIVE".equals(user.getStatus())) user.setStatus("ACTIVE");
                if (isAdminEmail) {
                    user.setRole("ADMIN");
                    if (user.getZoneCode() == null) user.setZoneCode("ZONA-SDMC-2026");
                }
                user.setUpdatedAt(LocalDateTime.now());
                user = repository.save(user);
                System.out.println("[GOOGLE-AUTH] Usuario autenticado (" + user.getRole() + "): " + email);
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

    // ─── VINCULAR OPERADOR A CÓDIGO DE ZONA DEL ADMIN ─────────────────────────────
    @Transactional
    public Map<String, Object> linkUserToZone(String email, String zoneCodeInput) {
        if (zoneCodeInput == null || zoneCodeInput.isBlank()) {
            throw new RuntimeException("El código de zona es requerido.");
        }

        String cleanCode = zoneCodeInput.trim().toUpperCase();

        // Verificar que el código corresponda a un Admin o sea ZONA-SDMC-2026
        boolean zoneExists = repository.findAll().stream()
                .anyMatch(u -> "ADMIN".equalsIgnoreCase(u.getRole()) && cleanCode.equalsIgnoreCase(u.getZoneCode()));

        if (!zoneExists && !"ZONA-SDMC-2026".equalsIgnoreCase(cleanCode)) {
            throw new RuntimeException("El Código de Zona '" + cleanCode + "' no es válido o no existe un Administrador con ese código.");
        }

        Usuario user = repository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        user.setZoneCode(cleanCode);
        user.setUpdatedAt(LocalDateTime.now());
        repository.save(user);

        System.out.printf("[ZONE-LINK] Operador %s vinculado exitosamente a la Zona %s%n", email, cleanCode);

        return Map.of(
            "success", true,
            "message", "¡Te has vinculado exitosamente a la Zona " + cleanCode + "!",
            "user", buildUserDto(user)
        );
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
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String generateSecureResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashResetToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    private void validateNewPassword(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 72
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*\\d.*")) {
            throw new RuntimeException("La contraseña debe tener entre 8 y 72 caracteres, al menos una letra y un número.");
        }
    }

    private void sendVerificationEmail(String email, String name, String code) {
        emailService.sendVerificationEmail(email, name, code);
        System.out.printf("[AUTH] Correo de verificación aceptado para %s%n", email);
    }

    public Map<String, Object> buildUserDto(Usuario user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",                  user.getId().toString());
        dto.put("name",                user.getName());
        dto.put("email",               user.getEmail());
        dto.put("status",              user.getStatus());
        dto.put("role",                user.getRole() != null ? user.getRole() : "OPERADOR");
        dto.put("zoneCode",            user.getZoneCode());
        dto.put("requiresZoneLinking", "OPERADOR".equals(user.getRole()) && (user.getZoneCode() == null || user.getZoneCode().isBlank()));
        dto.put("mfaEnabled",          user.isMfaEnabled());
        dto.put("picture",             "");
        return dto;
    }
}
