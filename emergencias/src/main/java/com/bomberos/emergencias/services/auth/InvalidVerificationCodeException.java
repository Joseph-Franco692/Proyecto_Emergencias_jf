package com.bomberos.emergencias.services.auth;

/**
 * Error de código de verificación inválido que debe conservar el contador de
 * intentos aun cuando la solicitud termine con una respuesta HTTP de error.
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException(String message) {
        super(message);
    }
}
