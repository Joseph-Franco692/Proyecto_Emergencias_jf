. "$PSScriptRoot\DockerCommon.ps1"
$root = Get-ProjectRoot
Import-DotEnv (Join-Path $root ".env.docker")
Assert-Docker
docker stack services $env:STACK_NAME
docker stack ps $env:STACK_NAME --no-trunc

