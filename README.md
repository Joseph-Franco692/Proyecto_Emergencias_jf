# Gestión Bomberil Distribuida

Plataforma web para el registro ciudadano de emergencias, despacho de unidades, operación en campo, monitoreo IoT post-incendio y prevención mediante el Plan Premium. El proyecto fue desarrollado para la asignatura **Aplicaciones Distribuidas**.

> Repositorio: **agrega aquí tu enlace de GitHub antes de entregar.**

## Capacidades principales

- Registro ciudadano de incidentes con geolocalización y evidencia multimedia.
- Dashboard administrativo con alertas y despacho de unidades en tiempo real.
- Módulo de operador: selección de unidad, recepción de asignaciones, ruta y cierre de atención.
- WebSocket con STOMP para la propagación de alertas, despachos y actualizaciones operativas.
- Nodo ESP32/MQ-2 para la evaluación post-incendio de gas/humo y habitabilidad.
- Autenticación con JWT, Google OAuth 2.0, OTP por correo, recuperación de contraseña y TOTP.
- Plan Premium de prevención con PayPal Sandbox.
- Evidencias en PocketBase, metadatos transaccionales en PostgreSQL e integridad SHA-256.
- Copiloto operativo con Ollama y el modelo local `llama3.2`, limitado al contexto del sistema.
- Despliegue con Docker Swarm: 2 réplicas del frontend, 2 del backend y una única instancia de PostgreSQL.

## Arquitectura resumida

```text
Angular + Nginx (2 réplicas)
        │ REST / WebSocket-STOMP
        ▼
Spring Boot (2 réplicas) ── PostgreSQL (1 instancia)
        ├── PocketBase: archivos privados
        ├── Ollama: copiloto operativo local
        ├── Gmail SMTP: OTP y recuperación de cuenta
        ├── PayPal Sandbox: órdenes del Plan Premium
        └── ngrok: acceso HTTPS para el nodo ESP32
```

La información de negocio se conserva en PostgreSQL. PocketBase almacena los archivos de evidencia; el backend valida autorización antes de entregarlos al cliente.

## Tecnologías

| Componente | Tecnología |
|---|---|
| Frontend | Angular 21, TypeScript, Nginx |
| Backend | Java 25, Spring Boot 4, Spring Security, JPA |
| Tiempo real | WebSocket, STOMP y broker simple de Spring |
| Datos | PostgreSQL 17 y PocketBase |
| IA | Ollama + llama3.2 |
| IoT | ESP32, sensor MQ-2, LCD I2C, buzzer y LED RGB |
| Pagos | PayPal Sandbox |
| Infraestructura | Docker Swarm, Docker Overlay Network y ngrok |

## Requisitos previos

- Docker Desktop iniciado.
- PowerShell 5.1 o superior.
- Cuenta Gmail con contraseña de aplicación para correos.
- Credenciales Sandbox de PayPal.
- Cuenta y dominio reservado de ngrok para el acceso del ESP32.
- Credenciales técnicas de PocketBase si se utilizará almacenamiento remoto.

## Configuración segura

1. Copia el archivo de ejemplo:

   ```powershell
   Copy-Item .env.example .env.docker
   ```

2. Completa `.env.docker` con tus credenciales reales. No publiques ese archivo.

3. Como alternativa, ejecuta el asistente interactivo:

   ```powershell
   .\scripts\configurar-docker.ps1
   ```

4. Configura el correo de Gmail con una contraseña de aplicación:

   ```powershell
   .\scripts\configurar-correo.ps1
   ```

## Ejecutar con Docker Swarm

```powershell
.\scripts\iniciar-swarm.ps1
```

El script construye las imágenes locales, inicializa Swarm si es necesario, despliega el stack y descarga el modelo de Ollama definido en `OLLAMA_MODEL`.

Para consultar el estado:

```powershell
.\scripts\estado-swarm.ps1
```

Para detener el stack sin borrar datos:

```powershell
.\scripts\detener-swarm.ps1
```

Direcciones predeterminadas:

| Servicio | Dirección |
|---|---|
| Aplicación | `http://localhost:8080` |
| API de salud | `http://localhost:8082/api/health` |
| PocketBase | `http://localhost:8091/_/` |
| Ollama | `http://localhost:11435` |
| Inspector ngrok | `http://localhost:4041` |

## Demostración de réplicas

El stack declara dos réplicas para `frontend` y `backend`.

```powershell
docker service ls
docker service ps gestion-bomberil_backend
docker service ps gestion-bomberil_frontend
```

Para una demostración controlada, detén una tarea de frontend o backend desde Docker Desktop. Swarm programará una nueva tarea para recuperar la cantidad deseada de réplicas. No detengas PostgreSQL durante la demostración porque la consigna establece una sola instancia de base de datos.

## Comunicación y procesamiento

- **Síncrona:** Angular, ESP32 y las integraciones externas consumen la API REST de Spring Boot.
- **En tiempo real:** STOMP sobre WebSocket notifica nuevos reportes, despachos, cambios de estado y telemetría a los clientes conectados.
- **Procesamiento diferido:** la carga de evidencia se ejecuta con `@Async` y un `ThreadPoolTaskExecutor` local.

> Nota académica: actualmente el proyecto no incorpora una cola persistente externa como RabbitMQ o Kafka. El broker STOMP es para eventos en vivo, no una cola persistente. Si el docente exige estrictamente una cola de mensajes, debe añadirse antes de la entrega final.

## Evidencias multimedia

1. El ciudadano adjunta una imagen o video al reporte.
2. Spring Boot calcula SHA-256 y guarda metadatos, asociación y hash en PostgreSQL.
3. El archivo se almacena en PocketBase.
4. La visualización pasa por un endpoint del backend protegido con JWT y permisos.

## Proyecto IoT

Consulta [iot-esp32/README.md](iot-esp32/README.md) para el cableado, las constantes, los umbrales y la configuración del nodo. Nunca publiques las credenciales Wi-Fi, `IOT_NODE_KEY` ni el token de ngrok.

## Documentación incluida

- [DOCKER_SWARM.md](DOCKER_SWARM.md): despliegue, respaldo y restauración.
- [POCKETBASE_EVIDENCIAS.md](POCKETBASE_EVIDENCIAS.md): configuración de evidencias privadas.
- [ENTREGABLES.md](ENTREGABLES.md): lista de verificación para la entrega.
- `documentacion/figuras/`: capturas de arquitectura, Docker Swarm y evidencias.

## Seguridad

- No subas `.env.docker`, respaldos, tokens, contraseñas ni secretos TOTP.
- Usa únicamente credenciales Sandbox para PayPal.
- Renueva cualquier token que haya sido expuesto en una consola, captura o chat.
- Antes de publicar, verifica que `.gitignore` esté activo y que `git status` no muestre secretos.
