$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$destination = Join-Path $root ".env.docker"

if (-not (Test-Path -LiteralPath $destination)) {
    throw "No existe .env.docker. Ejecute primero scripts\configurar-docker.ps1."
}

function Read-Secret([string]$Message) {
    $secure = Read-Host $Message -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

Write-Host "Configuración segura de Gmail SMTP."
Write-Host "Use una contraseña de aplicación de Google, no la contraseña normal de la cuenta."

$mailUser = (Read-Host "Correo Gmail remitente").Trim().ToLowerInvariant()
if (-not $mailUser.EndsWith("@gmail.com", [StringComparison]::OrdinalIgnoreCase)) {
    throw "Para la configuración actual el remitente debe ser una cuenta @gmail.com."
}

$mailPassword = (Read-Secret "Contraseña de aplicación Gmail (16 caracteres)") -replace "\s", ""
if ($mailPassword.Length -ne 16) {
    throw "La contraseña de aplicación debe tener exactamente 16 caracteres."
}

$lines = [System.Collections.Generic.List[string]](Get-Content -LiteralPath $destination)
$values = @{
    "MAIL_USERNAME" = $mailUser
    "MAIL_PASSWORD" = $mailPassword
}

foreach ($name in $values.Keys) {
    $replacement = "$name=$($values[$name])"
    $index = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^$([regex]::Escape($name))=") {
            $index = $i
            break
        }
    }
    if ($index -ge 0) {
        $lines[$index] = $replacement
    } else {
        $lines.Add($replacement)
    }
}

$temporary = "$destination.tmp"
[IO.File]::WriteAllLines($temporary, $lines, [Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath $temporary -Destination $destination -Force

Write-Host "Correo actualizado en .env.docker. La clave no fue mostrada."
Write-Host "Ejecute .\scripts\iniciar-swarm.ps1 para aplicar el cambio a las réplicas."
