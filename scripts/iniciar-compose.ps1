. "$PSScriptRoot\DockerCommon.ps1"
$root = Get-ProjectRoot
Import-DotEnv (Join-Path $root ".env.docker")
Assert-Configuration
Assert-Docker

Push-Location $root
try {
    docker compose --env-file .env.docker up -d --build
    if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar Docker Compose." }
    $ollamaContainer = docker compose --env-file .env.docker ps -q ollama
    Write-Host "Instalando el modelo $env:OLLAMA_MODEL en Ollama..."
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        docker exec $ollamaContainer ollama list *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 2
    }
    docker exec $ollamaContainer ollama pull $env:OLLAMA_MODEL
    if ($LASTEXITCODE -ne 0) { throw "La pila inició, pero no se pudo descargar el modelo de Ollama." }
    docker compose --env-file .env.docker ps
    Write-Host "Sistema: http://localhost:$env:FRONTEND_PORT"
    Write-Host "Backend: http://localhost:$env:BACKEND_PORT/api/health"
    Write-Host "PocketBase: http://localhost:$env:POCKETBASE_PORT/_/"
    Write-Host "Inspector ngrok: http://localhost:$env:NGROK_INSPECT_PORT"
} finally { Pop-Location }

