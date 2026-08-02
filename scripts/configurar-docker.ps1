$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$destination = Join-Path $root ".env.docker"

if (Test-Path $destination) {
    throw "Ya existe .env.docker. Se conservó intacto para no reemplazar credenciales."
}

function Read-Secret([string]$Message) {
    $secure = Read-Host $Message -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function New-HexSecret([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    }
    finally {
        $rng.Dispose()
    }
    return (($buffer | ForEach-Object { $_.ToString("x2") }) -join "")
}

Write-Host "Las claves se guardarán solo en .env.docker, excluido de Git."
$iotKey = Read-Secret "IOT_NODE_KEY (la misma del ESP32)"
$pocketbaseToken = Read-Secret "Token API de PocketBase (Enter si usaras credenciales tecnicas)"
$pocketbaseEmail = (Read-Host "Correo para usuario tecnico de PocketBase").Trim()
$pocketbasePassword = ""
if ($pocketbaseEmail) { $pocketbasePassword = Read-Secret "Contrasena local de PocketBase" }
$paypalClientId = Read-Secret "PayPal sandbox Client ID"
$paypalClientSecret = Read-Secret "PayPal sandbox Client Secret"
$ngrokToken = Read-Secret "Authtoken real de ngrok"
$ngrokDomain = (Read-Host "Dominio ngrok sin https://").Trim()
$mailUser = (Read-Host "Correo Gmail para MFA (Enter para omitir)").Trim()
$mailPassword = ""
if ($mailUser) {
    $mailPassword = (Read-Secret "Contraseña de aplicación Gmail (16 caracteres, no la contraseña normal)") -replace "\s", ""
    if ($mailUser.EndsWith("@gmail.com", [StringComparison]::OrdinalIgnoreCase) -and $mailPassword.Length -ne 16) {
        throw "Gmail requiere una contraseña de aplicación de 16 caracteres. No use la contraseña normal de su cuenta."
    }
}

$lines = @(
    "STACK_NAME=gestion-bomberil",
    "POSTGRES_DB=bomberos_db",
    "POSTGRES_USER=postgres",
    "POSTGRES_PASSWORD=$(New-HexSecret 24)",
    "IOT_NODE_KEY=$iotKey",
    "JWT_SECRET_KEY=$(New-HexSecret 48)",
    "MAIL_USERNAME=$mailUser",
    "MAIL_PASSWORD=$mailPassword",
    "PAYPAL_CLIENT_ID=$paypalClientId",
    "PAYPAL_CLIENT_SECRET=$paypalClientSecret",
    "PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com",
    "POCKETBASE_COLLECTION=evidencias_archivo",
    "POCKETBASE_TOKEN=$pocketbaseToken",
    "POCKETBASE_SUPERUSER_EMAIL=$pocketbaseEmail",
    "POCKETBASE_SUPERUSER_PASSWORD=$pocketbasePassword",
    "OLLAMA_MODEL=llama3.2",
    "NGROK_DOMAIN=$ngrokDomain",
    "NGROK_AUTHTOKEN=$ngrokToken",
    "FRONTEND_PORT=8080",
    "BACKEND_PORT=8082",
    "POSTGRES_PORT=5433",
    "POCKETBASE_PORT=8091",
    "OLLAMA_PORT=11435",
    "NGROK_INSPECT_PORT=4041"
)

[IO.File]::WriteAllLines($destination, $lines, [Text.UTF8Encoding]::new($false))
Write-Host ".env.docker creado. No comparta este archivo ni lo suba a Git."
