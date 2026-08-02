$ErrorActionPreference = "Stop"

function Import-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "No existe $Path. Copie .env.docker.example como .env.docker y complete sus datos."
    }
    foreach ($rawLine in Get-Content -LiteralPath $Path) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) { continue }
        $separator = $line.IndexOf("=")
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Assert-Docker {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop no está iniciado. Ábralo y espere a que indique 'Engine running'."
    }
}

function Assert-Configuration {
    $required = @("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD",
        "IOT_NODE_KEY", "JWT_SECRET_KEY", "POCKETBASE_COLLECTION", "OLLAMA_MODEL")
    foreach ($name in $required) {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")
        if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith("CAMBIAR")) {
            throw "Falta configurar $name en .env.docker."
        }
    }
}

function Get-ProjectRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

