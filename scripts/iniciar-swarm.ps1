. "$PSScriptRoot\DockerCommon.ps1"
$root = Get-ProjectRoot
Import-DotEnv (Join-Path $root ".env.docker")
Assert-Configuration
Assert-Docker

Push-Location $root
try {
    $swarmState = (docker info --format "{{.Swarm.LocalNodeState}}").Trim()
    if ($swarmState -eq "inactive") {
        docker swarm init
        if ($LASTEXITCODE -ne 0) { throw "No se pudo inicializar Docker Swarm." }
    } elseif ($swarmState -ne "active") {
        throw "Docker Swarm está en estado '$swarmState'."
    }

    docker build -t gestion-bomberil-backend:local .\emergencias
    if ($LASTEXITCODE -ne 0) { throw "Falló la imagen del backend." }
    docker build -t gestion-bomberil-frontend:local .\central-bomberos
    if ($LASTEXITCODE -ne 0) { throw "Falló la imagen del frontend." }
    docker build -t gestion-bomberil-pocketbase:local .\docker\pocketbase
    if ($LASTEXITCODE -ne 0) { throw "Falló la imagen de PocketBase." }
    docker pull postgres:17-alpine
    docker pull ollama/ollama:latest
    docker pull ngrok/ngrok:latest

    docker stack deploy --resolve-image never --compose-file docker-stack.yml $env:STACK_NAME
    if ($LASTEXITCODE -ne 0) { throw "No se pudo desplegar el stack." }

    $ollamaContainer = $null
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $ollamaContainer = docker ps --filter "label=com.docker.swarm.service.name=$env:STACK_NAME`_ollama" --format "{{.ID}}" | Select-Object -First 1
        if ($ollamaContainer) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $ollamaContainer) { throw "Ollama no inició. Revise los logs del servicio." }
    docker exec $ollamaContainer ollama pull $env:OLLAMA_MODEL
    if ($LASTEXITCODE -ne 0) { throw "El stack inició, pero falló la descarga del modelo." }

    docker stack services $env:STACK_NAME
    Write-Host "Sistema: http://localhost:$env:FRONTEND_PORT"
    Write-Host "Backend: http://localhost:$env:BACKEND_PORT/api/health"
    Write-Host "PocketBase: http://localhost:$env:POCKETBASE_PORT/_/"
    Write-Host "Inspector ngrok: http://localhost:$env:NGROK_INSPECT_PORT"
} finally { Pop-Location }
