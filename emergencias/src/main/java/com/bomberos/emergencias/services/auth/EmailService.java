package com.bomberos.emergencias.services.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.password}")
    private String mailPassword;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    /**
     * Envía el correo de verificación de cuenta de forma asíncrona.
     * El código de 6 dígitos tiene vigencia de 10 minutos.
     */
    public void sendVerificationEmail(String to, String name, String code) {
        validateConfiguration();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("🔐 Código de Verificación - Central de Bomberos");

            String html = buildVerificationEmailHtml(name, to, code);
            helper.setText(html, true);

            mailSender.send(message);
            System.out.printf("[EMAIL] Correo de verificación enviado a: %s%n", to);

        } catch (MessagingException | MailException e) {
            System.err.printf("[EMAIL] El proveedor rechazó el correo de verificación para %s%n", to);
            throw deliveryFailure(e);
        }
    }

    /**
     * Envía un enlace de recuperación de un solo uso. La contraseña nunca se
     * incluye en el correo ni se almacena sin hash.
     */
    public void sendPasswordResetEmail(String to, String name, String resetUrl, int validMinutes) {
        validateConfiguration();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Recuperación de contraseña - Gestión Bomberil");
            helper.setText(buildPasswordResetEmailHtml(name, resetUrl, validMinutes), true);

            mailSender.send(message);
            System.out.printf("[EMAIL] Recuperación de contraseña enviada a: %s%n", to);
        } catch (MessagingException | MailException e) {
            System.err.printf("[EMAIL] El proveedor rechazó el correo de recuperación para %s%n", to);
            throw deliveryFailure(e);
        }
    }

    private void validateConfiguration() {
        if (fromEmail == null || fromEmail.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            throw new EmailDeliveryException(
                    "El servicio de correo no está configurado. Contacta al administrador."
            );
        }

        if (fromEmail.toLowerCase().endsWith("@gmail.com")
                && mailPassword.replaceAll("\\s+", "").length() != 16) {
            throw new EmailDeliveryException(
                    "El servicio de correo requiere una contraseña de aplicación válida de Gmail."
            );
        }
    }

    private EmailDeliveryException deliveryFailure(Exception cause) {
        return new EmailDeliveryException(
                "No se pudo entregar el correo en este momento. Verifica la configuración SMTP e inténtalo nuevamente.",
                cause
        );
    }

    private String buildVerificationEmailHtml(String name, String email, String code) {
        String baseUrl = frontendUrl.replaceAll("/+$", "");
        String verificationUrl = baseUrl + "/login?verifyEmail="
                + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#07090d;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#07090d;padding:40px 20px;">
                <tr>
                  <td align="center">
                    <table width="500" cellpadding="0" cellspacing="0" style="background:#111520;border-radius:16px;overflow:hidden;border:1px solid rgba(255,68,68,0.2);">
                      <!-- Header -->
                      <tr>
                        <td style="background:linear-gradient(135deg,#dc2626,#b91c1c);padding:28px 32px;text-align:center;">
                          <div style="font-size:32px;margin-bottom:8px;">🚒</div>
                          <h1 style="margin:0;color:#fff;font-size:20px;font-weight:800;letter-spacing:1px;">CENTRAL DE BOMBEROS</h1>
                          <p style="margin:4px 0 0;color:rgba(255,255,255,0.75);font-size:13px;">Santo Domingo · Ecuador</p>
                        </td>
                      </tr>
                      <!-- Body -->
                      <tr>
                        <td style="padding:32px;">
                          <p style="color:#cbd5e1;font-size:16px;margin:0 0 8px;">Hola, <strong style="color:#f1f5f9;">%s</strong></p>
                          <p style="color:#94a3b8;font-size:14px;line-height:1.7;margin:0 0 24px;">
                            Recibimos tu solicitud para crear una cuenta en el Sistema de Gestión de Emergencias.
                            Usa el siguiente código para verificar tu correo electrónico:
                          </p>
                          <!-- Code box -->
                          <div style="background:#0f1218;border:2px solid #dc2626;border-radius:12px;padding:24px;text-align:center;margin-bottom:24px;">
                            <p style="color:#64748b;font-size:12px;margin:0 0 12px;letter-spacing:2px;text-transform:uppercase;">Código de verificación</p>
                            <span style="font-size:42px;font-weight:900;color:#dc2626;letter-spacing:14px;display:block;">%s</span>
                            <p style="color:#475569;font-size:12px;margin:12px 0 0;">⏱ Válido por 10 minutos</p>
                          </div>
                          <div style="text-align:center;margin:0 0 24px;">
                            <a href="%s" style="display:inline-block;padding:13px 22px;background:#dc2626;color:#fff;text-decoration:none;border-radius:10px;font-weight:bold;">
                              Verificar mi cuenta
                            </a>
                          </div>
                          <p style="color:#64748b;font-size:13px;line-height:1.6;margin:0;">
                            Si no solicitaste este código, ignora este correo. Tu cuenta permanecerá inactiva.
                          </p>
                        </td>
                      </tr>
                      <!-- Footer -->
                      <tr>
                        <td style="padding:16px 32px;border-top:1px solid rgba(255,255,255,0.05);text-align:center;">
                          <p style="color:#334155;font-size:11px;margin:0;">
                            🔒 Este es un mensaje automático. No responder a este correo.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(name, code, verificationUrl);
    }

    private String buildPasswordResetEmailHtml(String name, String resetUrl, int validMinutes) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#0b1420;font-family:Arial,Helvetica,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;background:#0b1420;">
                <tr><td align="center">
                  <table width="520" cellpadding="0" cellspacing="0" style="max-width:100%%;background:#152535;border:1px solid #30465b;border-radius:18px;overflow:hidden;">
                    <tr><td style="padding:28px 32px;background:#203b54;color:#fff;">
                      <h1 style="margin:0;font-size:22px;">Gestión Bomberil</h1>
                      <p style="margin:6px 0 0;color:#bed0df;font-size:13px;">Recuperación segura de acceso</p>
                    </td></tr>
                    <tr><td style="padding:32px;color:#e8f0f6;">
                      <p style="margin:0 0 12px;font-size:16px;">Hola, <strong>%s</strong>.</p>
                      <p style="margin:0 0 24px;color:#bdcad5;font-size:14px;line-height:1.7;">
                        Recibimos una solicitud para cambiar la contraseña de tu cuenta.
                        El enlace funciona una sola vez y vence en %d minutos.
                      </p>
                      <div style="text-align:center;margin:28px 0;">
                        <a href="%s" style="display:inline-block;padding:14px 24px;background:#d9433f;color:#fff;text-decoration:none;border-radius:10px;font-weight:bold;">
                          Crear nueva contraseña
                        </a>
                      </div>
                      <p style="margin:0;color:#8fa3b4;font-size:12px;line-height:1.6;">
                        Si no solicitaste este cambio, ignora el mensaje. Tu contraseña actual continuará funcionando.
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, validMinutes, resetUrl);
    }
}
