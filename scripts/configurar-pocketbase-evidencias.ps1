$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$destination = Join-Path $root ".env.docker"

if (-not (Test-Path $destination)) {
    throw "No existe .env.docker. Primero ejecuta .\scripts\configurar-docker.ps1"
}

function Read-Secret([string]$Message) {
    $secure = Read-Host $Message -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$email = (Read-Host "Correo para el usuario tecnico de PocketBase").Trim()
if ([string]::IsNullOrWhiteSpace($email)) { throw "El correo es obligatorio." }
$password = Read-Secret "Contrasena local de PocketBase (minimo 10 caracteres)"
if ($password.Length -lt 10) { throw "La contrasena debe tener al menos 10 caracteres." }

$values = @{
    "POCKETBASE_SUPERUSER_EMAIL" = $email
    "POCKETBASE_SUPERUSER_PASSWORD" = $password
}
$lines = [System.Collections.Generic.List[string]](Get-Content $destination)
foreach ($key in $values.Keys) {
    $index = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ("^" + [regex]::Escape($key) + "=")) { $index = $i; break }
    }
    $line = "$key=$($values[$key])"
    if ($index -ge 0) { $lines[$index] = $line } else { $lines.Add($line) }
}

[IO.File]::WriteAllLines($destination, $lines, [Text.UTF8Encoding]::new($false))
Write-Host "Credenciales tecnicas guardadas en .env.docker. Ahora ejecuta .\scripts\iniciar-swarm.ps1"
