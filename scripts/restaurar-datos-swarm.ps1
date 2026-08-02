param(
    [string]$BackupDirectory = "",
    [switch]$Confirmar
)

. "$PSScriptRoot\DockerCommon.ps1"
$root = Get-ProjectRoot
Import-DotEnv (Join-Path $root ".env.docker")
Assert-Docker

if (-not $Confirmar) {
    throw "Operación protegida. Use -Confirmar solo en la instalación nueva; reemplaza PostgreSQL y PocketBase."
}
if (-not $BackupDirectory) {
    $latest = Get-ChildItem (Join-Path $root "backups") -Directory |
        Where-Object Name -Like "pre-swarm-*" |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $latest) { throw "No se encontró un respaldo pre-swarm." }
    $BackupDirectory = $latest.FullName
}

$backup = (Resolve-Path $BackupDirectory).Path
$dump = Join-Path $backup "postgres-bomberos_db.dump"
$pbData = Join-Path $backup "pocketbase-pb_data"
if (-not (Test-Path $dump) -or -not (Test-Path $pbData)) {
    throw "El respaldo no contiene PostgreSQL y PocketBase completos."
}

$stack = $env:STACK_NAME
$postgresService = "$stack`_postgres"
$pocketbaseService = "$stack`_pocketbase"
$pocketbaseVolume = "$stack`_pocketbase_data"

Write-Host "Deteniendo temporalmente backend y PocketBase..."
docker service scale "$stack`_backend=0" "$pocketbaseService=0"
Start-Sleep -Seconds 5

Write-Host "Restaurando PocketBase..."
docker run --rm -v "${pbData}:/backup:ro" -v "${pocketbaseVolume}:/data" alpine:3.22 sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; cp -a /backup/. /data/"
if ($LASTEXITCODE -ne 0) { throw "Falló la restauración de PocketBase." }

$postgresContainer = docker ps --filter "label=com.docker.swarm.service.name=$postgresService" --format "{{.ID}}" | Select-Object -First 1
if (-not $postgresContainer) { throw "No se encontró PostgreSQL." }
docker cp $dump "${postgresContainer}:/tmp/bomberos.dump"
docker exec -e PGPASSWORD=$env:POSTGRES_PASSWORD $postgresContainer pg_restore `
    --username $env:POSTGRES_USER --dbname $env:POSTGRES_DB `
    --clean --if-exists --no-owner --no-privileges /tmp/bomberos.dump
if ($LASTEXITCODE -ne 0) { throw "Falló la restauración de PostgreSQL." }

docker service scale "$pocketbaseService=1" "$stack`_backend=1"
Write-Host "Restauración terminada. Renueve POCKETBASE_TOKEN si expiró o fue revocado."

