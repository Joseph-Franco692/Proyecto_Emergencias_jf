package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.services.auth.AuthService;
import com.bomberos.emergencias.services.auth.MfaService;
import dev.samstevens.totp.exceptions.QrGenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final AuthService authService;
    private final MfaService mfaService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.loginManualFirstStep(
                request.get("email"),
                request.get("password")
        ));
    }

    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setup(@RequestBody Map<String, String> request) throws QrGenerationException {
        return ResponseEntity.ok(mfaService.setupMfa(
                request.get("email")
        ));
    }

    @PostMapping("/confirm-setup")
    public ResponseEntity<Map<String, Object>> confirmSetup(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(mfaService.confirmMfa(
                request.get("email"),
                request.get("code")
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(mfaService.verifyMfa(
                request.get("email"),
                request.get("code")
        ));
    }
}
