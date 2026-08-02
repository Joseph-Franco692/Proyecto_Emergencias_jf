. "$PSScriptRoot\DockerCommon.ps1"
$root = Get-ProjectRoot
Import-DotEnv (Join-Path $root ".env.docker")
Assert-Docker
Push-Location $root
try { docker compose --env-file .env.docker down } finally { Pop-Location }

