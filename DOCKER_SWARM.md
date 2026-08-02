# Gestión Bomberil en Docker Swarm

La pila contiene Angular/Nginx, Spring Boot, PostgreSQL, PocketBase, Ollama y
ngrok. PostgreSQL, PocketBase, Ollama y las cargas locales usan volúmenes
persistentes.

## Preparación

1. Abra Docker Desktop y espere `Engine running`.
2. Ejecute `.\scripts\configurar-docker.ps1` y responda sus preguntas.
3. El asistente crea `.env.docker`; `NGROK_DOMAIN` se escribe sin `https://`.
4. Use una clave JWT aleatoria de al menos 64 caracteres.
5. `IOT_NODE_KEY` debe coincidir con la clave grabada en el ESP32.

`.env.docker` y `backups/` están excluidos de Git.

## Prueba segura con Compose

```powershell
.\scripts\iniciar-compose.ps1
```

Esta fase usa puertos alternos para convivir con el sistema actual. Abra
`http://localhost:8080`.

```powershell
.\scripts\detener-compose.ps1
```

## Despliegue en Swarm

```powershell
.\scripts\iniciar-swarm.ps1
```

El comando inicializa un Swarm de un nodo, construye las imágenes, despliega
los servicios y descarga el modelo de Ollama. En varios nodos será necesario
publicar las imágenes en un registro; las etiquetas `:local` solo existen en
el equipo que las construyó.

```powershell
.\scripts\estado-swarm.ps1
.\scripts\detener-swarm.ps1
```

Detener el stack no borra los datos. No ejecute `docker volume rm` ni
`docker system prune --volumes`.

## Restaurar el respaldo actual

Primero compruebe que el stack vacío inicia. La restauración está separada y
protegida porque reemplaza datos:

```powershell
.\scripts\restaurar-datos-swarm.ps1 -Confirmar
```

Selecciona el respaldo `pre-swarm-*` más reciente, restaura PostgreSQL y copia
`pb_data` con PocketBase detenido.

## Direcciones

- Aplicación: `http://localhost:8080`
- Backend: `http://localhost:8082/api/health`
- PocketBase: `http://localhost:8091/_/`
- Ollama: `http://localhost:11435`
- Inspector ngrok: `http://localhost:4041`

Angular usa `/api` y `/ws-emergencias` por el proxy Nginx. Dentro de Docker,
Spring encuentra `postgres`, `pocketbase` y `ollama` por DNS de servicio.

## Seguridad

- Renueve el token PocketBase que apareció anteriormente en consola/chat.
- Nunca suba `.env.docker` ni los respaldos a Git.
- En un servidor real, migre las claves a Docker Secrets y no publique
  PostgreSQL, PocketBase ni Ollama a Internet.
- PocketBase sigue antes de la versión 1.0; haga respaldo antes de actualizar.
