. "$PSScriptRoot\DockerCommon.ps1"
$root = Get-ProjectRoot
Import-DotEnv (Join-Path $root ".env.docker")
Assert-Docker
docker stack rm $env:STACK_NAME

