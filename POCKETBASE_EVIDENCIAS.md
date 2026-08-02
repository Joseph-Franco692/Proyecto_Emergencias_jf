# PocketBase para evidencias multimedia

## Arquitectura

- PostgreSQL mantiene reportes, usuarios, incidentes, unidades, relaciones y metadatos.
- PocketBase almacena el archivo binario de cada foto o video.
- Spring Boot calcula SHA-256, sube el archivo y lo entrega a Angular mediante un endpoint propio.
- Angular no necesita credenciales de PocketBase.

## 1. Instalar y arrancar PocketBase

1. Descarga PocketBase para Windows desde https://pocketbase.io/docs/
2. Descomprímelo, por ejemplo, en `C:\PocketBase`.
3. Ejecuta:

```powershell
cd C:\PocketBase
.\pocketbase.exe serve
```

4. Abre `http://127.0.0.1:8090/_/` y crea el primer superusuario.

No borres `pb_data`: contiene la base y los archivos y debe incluirse en los respaldos.

## 2. Crear la colección

En el panel crea una colección **Base** llamada `evidencias_archivo` con:

| Campo | Tipo | Configuración |
|---|---|---|
| `archivo` | File | obligatorio, máximo 1, máximo 15 MB; MIME `image/jpeg,image/png,image/webp,video/mp4` |
| `sha256` | Text | obligatorio, máximo 64 |
| `reporteId` | Number | obligatorio, solo enteros |
| `mimeType` | Text | obligatorio |
| `tamanoBytes` | Number | obligatorio, solo enteros |

Deja las cinco reglas API bloqueadas. Solo Spring debe acceder.

## 3. Generar el token

En `Collections` abre `_superusers`, selecciona tu usuario y usa **Impersonate** para generar un token.
No lo pegues en Angular, Git ni capturas de pantalla.

## 4. Iniciar Spring con PocketBase

```powershell
$env:EVIDENCE_STORAGE_PROVIDER="pocketbase"
$env:POCKETBASE_URL="http://127.0.0.1:8090"
$env:POCKETBASE_COLLECTION="evidencias_archivo"
$env:POCKETBASE_TOKEN="PEGA_AQUI_TU_TOKEN"
cd C:\Users\josep\OneDrive\Documentos\PROYECTO_EMERGENCIAS\emergencias
.\mvnw.cmd spring-boot:run
```

Estas variables duran mientras la consola permanezca abierta. Para volver al almacenamiento anterior:

```powershell
$env:EVIDENCE_STORAGE_PROVIDER="local"
```

## 5. Comprobar

1. Mantén PocketBase y Spring ejecutándose.
2. Crea un reporte ciudadano con foto.
3. Abre el detalle: la imagen debe mostrarse igual que antes.
4. En PocketBase debe aparecer el registro con archivo y SHA-256.
5. PostgreSQL conserva la relación en `evidencias_multimedia`.

Las evidencias antiguas en `uploads/` siguen funcionando. Solo las cargas nuevas usan PocketBase cuando el proveedor está activo.

## Producción

PocketBase no es serverless: es un backend ejecutable con SQLite y almacenamiento de archivos. En producción necesita HTTPS, ejecución como servicio, volumen persistente y respaldos. Desde `Settings > Files storage` puede usar almacenamiento compatible con S3.
