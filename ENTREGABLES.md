# Lista de verificación de entrega

Este archivo permite organizar los elementos solicitados para el proyecto de Aplicaciones Distribuidas. Antes de enviar, completa el enlace del repositorio y verifica que no existan secretos expuestos.

## Entregables obligatorios

| Entregable | Ubicación o acción |
|---|---|
| Informe técnico en PDF | `INFORME_PROYECTO_BOMB_JFFFF.pdf` (adjuntar en la plataforma académica) |
| Repositorio actualizado | Agregar aquí el enlace de GitHub antes de entregar: `PENDIENTE` |
| Instrucciones de ejecución | [README.md](README.md) |
| Variables de entorno sin secretos | [.env.example](.env.example) y [.env.docker.example](.env.docker.example) |
| Configuración Docker Swarm | [docker-stack.yml](docker-stack.yml) |
| Automatización del despliegue | `scripts/iniciar-swarm.ps1`, `scripts/estado-swarm.ps1` y `scripts/detener-swarm.ps1` |

## Evidencias a presentar desde la aplicación

- [ ] Docker Swarm con `frontend` y `backend` en `2/2` réplicas.
- [ ] PostgreSQL en una sola instancia.
- [ ] Registro de ciudadano y reporte georreferenciado.
- [ ] Dashboard con alerta en tiempo real y despacho de una unidad.
- [ ] Operador recibe la asignación y finaliza la atención.
- [ ] Correo OTP de verificación, recuperación de contraseña y TOTP.
- [ ] Pago aprobado en PayPal Sandbox y consulta administrativa de la orden.
- [ ] Evidencia cargada en PocketBase y visualizada por un usuario autorizado.
- [ ] Solicitud de archivo sin sesión o permisos suficientes rechazada.
- [ ] Telemetría IoT asociada al reporte de cierre.
- [ ] Copiloto Ollama respondiendo solo con contexto operativo.
- [ ] Logs, excepciones controladas y pruebas de API.

## Revisión de seguridad antes de publicar

```powershell
git status
git check-ignore .env.docker
git grep -n -i "password\|secret\|authtoken\|client_secret" -- ":!*.example"
```

No publiques el archivo `.env.docker`, respaldos de base de datos, tokens, claves del ESP32, credenciales de Gmail, secretos TOTP ni credenciales reales de pago.

## Observación técnica pendiente

La consigna solicita una cola de mensajes para procesamiento asíncrono. El proyecto actual usa `@Async` con una cola en memoria de Spring y un broker STOMP para eventos WebSocket; ninguno sustituye una cola persistente externa. Para cumplimiento estricto de este punto, debe integrarse RabbitMQ, ActiveMQ o Kafka y documentar productor, consumidor, mensaje y resultado.
