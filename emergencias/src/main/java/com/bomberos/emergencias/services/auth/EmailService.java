package com.bomberos.emergencias.services.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Envía el correo de verificación de cuenta de forma asíncrona.
     * El código de 6 dígitos tiene vigencia de 10 minutos.
     */
    @Async("taskExecutor")
    public void sendVerificationEmail(String to, String name, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("🔐 Código de Verificación - Central de Bomberos");

            String html = buildVerificationEmailHtml(name, code);
            helper.setText(html, true);

            mailSender.send(message);
            System.out.printf("[EMAIL] Correo de verificación enviado a: %s%n", to);

        } catch (MessagingException e) {
            System.err.printf("[EMAIL] Error al enviar correo a %s: %s%n", to, e.getMessage());
            // Re-lanzamos para que el caller decida si mostrar el código en consola
            throw new RuntimeException("Error al enviar correo de verificación: " + e.getMessage(), e);
        }
    }

    private String buildVerificationEmailHtml(String name, String code) {
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
            """.formatted(name, code);
    }
}
