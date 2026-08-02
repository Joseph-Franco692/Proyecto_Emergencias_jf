#!/bin/sh
set -eu

PB_DIR="${PB_DIR:-/pb/pb_data}"

# Las migraciones crean la colecciÃ³n de evidencias antes de levantar la API.
/pb/pocketbase migrate up --dir="$PB_DIR" --migrationsDir=/pb/pb_migrations

# Esta cuenta es exclusivamente tÃ©cnica: el backend la usa para guardar y leer
# archivos; no se expone al navegador ni se guarda en Git.
if [ -n "${POCKETBASE_SUPERUSER_EMAIL:-}" ] && [ -n "${POCKETBASE_SUPERUSER_PASSWORD:-}" ]; then
  /pb/pocketbase superuser upsert "$POCKETBASE_SUPERUSER_EMAIL" "$POCKETBASE_SUPERUSER_PASSWORD" --dir="$PB_DIR"
fi

exec /pb/pocketbase serve --http=0.0.0.0:8090 --dir="$PB_DIR" --migrationsDir=/pb/pb_migrations
