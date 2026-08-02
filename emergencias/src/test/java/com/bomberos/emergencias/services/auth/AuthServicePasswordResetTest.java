package com.bomberos.emergencias.services.auth;

import com.bomberos.emergencias.models.Usuario;
import com.bomberos.emergencias.repositories.UsuarioRepository;
import com.bomberos.emergencias.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordResetTest {

    @Mock private UsuarioRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;

    @InjectMocks private AuthService service;

    @BeforeEach
    void configureFrontendUrl() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:8080");
    }

    @Test
    void solicitudGuardaSoloHashYEnviaEnlaceTemporal() {
        Usuario user = activeUser();
        when(repository.findByEmail("operador@correo.com")).thenReturn(Optional.of(user));

        Map<String, Object> response = service.requestPasswordReset(" OPERADOR@correo.com ");

        assertEquals(true, response.get("success"));
        assertNotNull(user.getResetToken());
        assertEquals(64, user.getResetToken().length());
        assertNotNull(user.getResetTokenExpiresAt());
        assertFalse(user.getResetTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(16)));

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                eq("operador@correo.com"), eq("Operador"), url.capture(), eq(15));
        String rawToken = url.getValue().substring(url.getValue().indexOf("resetToken=") + 11);
        assertNotEquals(rawToken, user.getResetToken());
        verify(repository).save(user);
    }

    @Test
    void respuestaNoRevelaSiElCorreoNoExiste() {
        when(repository.findByEmail("desconocido@correo.com")).thenReturn(Optional.empty());

        Map<String, Object> response = service.requestPasswordReset("desconocido@correo.com");

        assertEquals(true, response.get("success"));
        verifyNoInteractions(emailService);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tokenValidoCambiaPasswordYQuedaInvalidado() {
        Usuario user = activeUser();
        user.setResetToken("hash-buscado");
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(repository.findWithLockByResetToken(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("ClaveNueva2026")).thenReturn("bcrypt-nuevo");

        Map<String, Object> response = service.resetPassword("token-de-prueba", "ClaveNueva2026");

        assertEquals(true, response.get("success"));
        assertEquals("bcrypt-nuevo", user.getPasswordHash());
        assertEquals(null, user.getResetToken());
        assertEquals(null, user.getResetTokenExpiresAt());
        verify(repository).save(user);

        when(repository.findWithLockByResetToken(anyString())).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> service.resetPassword("token-de-prueba", "ClaveNueva2026"));
    }

    private Usuario activeUser() {
        Usuario user = new Usuario();
        user.setName("Operador");
        user.setEmail("operador@correo.com");
        user.setStatus("ACTIVE");
        user.setPasswordHash("bcrypt-anterior");
        return user;
    }
}
