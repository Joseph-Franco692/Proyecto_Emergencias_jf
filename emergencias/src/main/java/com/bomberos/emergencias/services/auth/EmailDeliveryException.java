package com.bomberos.emergencias.services.auth;

/**
 * Error controlado del canal de correo. Nunca expone credenciales ni detalles
 * internos de SMTP al cliente.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
