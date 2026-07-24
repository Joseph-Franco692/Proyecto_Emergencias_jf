package com.bomberos.emergencias.services.auth;

import com.bomberos.emergencias.models.Usuario;
import com.bomberos.emergencias.repositories.UsuarioRepository;
import com.bomberos.emergencias.security.JwtService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MfaService {

    private final UsuarioRepository repository;
    private final JwtService jwtService;

    /**
     * PASO 1 del setup 2FA:
     * Genera un secreto TOTP y lo guarda como pendingMfaSecret.
     * Devuelve el QR en base64 para mostrar al usuario.
     */
    @Transactional
    public Map<String, Object> setupMfa(String email) throws QrGenerationException {
        Usuario user = repository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("La cuenta debe estar activa para configurar 2FA.");
        }

        // Generar secreto TOTP de 32 caracteres (compatible con Google Authenticator)
        SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
        String secret = secretGenerator.generate();

        // Construir la URL otpauth://totp/...
        QrData data = new QrData.Builder()
            .label(user.getEmail())          // Identificador que aparece en el Authenticator
            .secret(secret)                   // La clave Base32
            .issuer("Central de Bomberos")    // Nombre de la app en el Authenticator
            .algorithm(HashingAlgorithm.SHA1) // SHA1 es lo estándar (Google Authenticator)
            .digits(6)                        // 6 dígitos TOTP
            .period(30)                       // Rotación cada 30 segundos
            .build();

        // Generar imagen PNG del QR
        QrGenerator generator = new ZxingPngQrGenerator();
        byte[] imageData    = generator.generate(data);
        String mimeType     = generator.getImageMimeType();
        String dataUri      = Utils.getDataUriForImage(imageData, mimeType);

        // Guardar el secreto PENDIENTE (se confirma en el paso 2)
        user.setPendingMfaSecret(secret);
        repository.save(user);

        System.out.printf("[MFA-SETUP] QR generado para %s%n", email);

        Map<String, Object> response = new HashMap<>();
        response.put("qrCode",    dataUri);   // data:image/png;base64,...
        response.put("manualKey", secret);    // Para entrada manual en el Authenticator
        response.put("email",     user.getEmail());
        response.put("issuer",    "Central de Bomberos");
        return response;
    }

    /**
     * PASO 2 del setup 2FA:
     * Valida el primer código del Authenticator para confirmar que el secreto se escaneó bien.
     * Si es correcto, activa mfaEnabled y devuelve el JWT de sesión.
     */
    @Transactional
    public Map<String, Object> confirmMfa(String email, String code) {
        if (email == null || code == null) throw new RuntimeException("Correo y código son requeridos.");

        Usuario user = repository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (user.getPendingMfaSecret() == null) {
            // Si ya estaba configurado y se reintenta, lo tratamos como verificación normal
            if (user.isMfaEnabled() && user.getMfaSecret() != null) {
                return verifyMfa(email, code);
            }
            throw new RuntimeException("No hay una configuración 2FA pendiente. Vuelve al paso anterior.");
        }

        // Verificar el código TOTP contra el secreto pendiente
        boolean valid = buildVerifier().isValidCode(user.getPendingMfaSecret(), code.trim());
        if (!valid) {
            throw new RuntimeException("Código incorrecto. Asegúrate de que la hora de tu dispositivo sea correcta e inténtalo de nuevo.");
        }

        // Confirmar: mover el secreto de pendiente a activo
        user.setMfaSecret(user.getPendingMfaSecret());
        user.setPendingMfaSecret(null);
        user.setMfaEnabled(true);
        repository.save(user);

        // Emitir JWT — el usuario ya queda autenticado
        String token = jwtService.generateToken(user);

        System.out.printf("[MFA-CONFIRM] 2FA configurado y activado para %s%n", email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token",   token);
        response.put("user",    buildUserDto(user));
        response.put("message", "Autenticación de dos factores configurada. ¡Bienvenido!");
        return response;
    }

    /**
     * Verificación normal de 2FA en cada login:
     * El usuario ya tiene mfaEnabled = true, valida su código e ingresa al sistema.
     */
    @Transactional
    public Map<String, Object> verifyMfa(String email, String code) {
        if (email == null || code == null) throw new RuntimeException("Correo y código son requeridos.");

        Usuario user = repository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            throw new RuntimeException("El 2FA no está configurado para este usuario.");
        }

        boolean valid = buildVerifier().isValidCode(user.getMfaSecret(), code.trim());
        if (!valid) {
            throw new RuntimeException("Código MFA incorrecto. Intenta de nuevo.");
        }

        String token = jwtService.generateToken(user);

        System.out.printf("[MFA-VERIFY] Acceso verificado para %s%n", email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token",   token);
        response.put("user",    buildUserDto(user));
        response.put("message", "Inicio de sesión exitoso.");
        return response;
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    /**
     * Construye el verificador TOTP con una ventana de ±1 período (±30 s)
     * para compensar pequeñas diferencias de reloj entre el servidor y el dispositivo.
     */
    private CodeVerifier buildVerifier() {
        TimeProvider    timeProvider = new SystemTimeProvider();
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);
        // Permite 1 período de tolerancia = ±30 segundos
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }

    private Map<String, Object> buildUserDto(Usuario user) {
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
