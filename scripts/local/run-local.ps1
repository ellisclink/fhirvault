# Runs fhirvault without Docker: Keycloak from its standalone distribution, the API with Maven,
# both against the PostgreSQL installed on this machine. Ctrl+C stops both.
#
#   powershell -ExecutionPolicy Bypass -File scripts\local\run-local.ps1           # with SMART security
#   powershell -ExecutionPolicy Bypass -File scripts\local\run-local.ps1 -NoAuth   # API only, open access
param([switch] $NoAuth)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
. (Join-Path $PSScriptRoot "env.ps1")
Import-DotEnv (Join-Path $root "deploy\.env")

$keycloakVersion = "26.8.0"
$localDir = Join-Path $root ".local"
$keycloakHome = Join-Path $localDir "keycloak-$keycloakVersion"
$keycloak = $null

function Wait-Http([string] $url, [int] $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try { Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 3 | Out-Null; return } catch { Start-Sleep -Seconds 2 }
    }
    throw "Timed out waiting for $url"
}

foreach ($port in 8080, 8081) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is in use. Stop the other process (Docker Compose, or scripts\local\stop-local.ps1) first."
    }
}

try {
    if (-not $NoAuth) {
        if (-not (Test-Path (Join-Path $keycloakHome "bin\kc.bat"))) {
            New-Item -ItemType Directory -Force $localDir | Out-Null
            $zip = Join-Path $localDir "keycloak-$keycloakVersion.zip"
            Write-Host "Downloading Keycloak $keycloakVersion (about 200 MB, first run only)..."
            $ProgressPreference = "SilentlyContinue"
            Invoke-WebRequest "https://github.com/keycloak/keycloak/releases/download/$keycloakVersion/keycloak-$keycloakVersion.zip" -OutFile $zip
            Expand-Archive $zip -DestinationPath $localDir -Force
            Remove-Item $zip
        }

        # Same realm file as Docker Compose. Keycloak skips the import if the realm already exists;
        # delete .local\keycloak-*\data to start fresh.
        $importDir = Join-Path $keycloakHome "data\import"
        New-Item -ItemType Directory -Force $importDir | Out-Null
        Copy-Item (Join-Path $root "deploy\keycloak\fhirvault-realm.json") $importDir -Force

        $env:KC_BOOTSTRAP_ADMIN_USERNAME = "admin"
        $env:KC_BOOTSTRAP_ADMIN_PASSWORD = $env:KEYCLOAK_ADMIN_PASSWORD
        $env:KC_HEALTH_ENABLED = "true"
        $log = Join-Path $localDir "keycloak.log"
        Write-Host "Starting Keycloak on http://localhost:8081 (log: .local\keycloak.log)..."
        $keycloak = Start-Process -FilePath (Join-Path $keycloakHome "bin\kc.bat") `
            -ArgumentList "start-dev", "--http-port=8081", "--import-realm" `
            -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden -PassThru
        Wait-Http "http://localhost:8081/realms/fhirvault/.well-known/openid-configuration" 180
        Write-Host "Keycloak is up."
        $env:KEYCLOAK_ISSUER_URI = "http://localhost:8081/realms/fhirvault"
        $profiles = "local"
    } else {
        $profiles = "local,noauth"
    }

    $env:DB_URL = "jdbc:postgresql://localhost:5432/fhirvault"
    $env:DB_USERNAME = "fhirvault"
    $env:DB_PASSWORD = $env:POSTGRES_PASSWORD
    Write-Host "Starting the API on http://localhost:8080/fhir (profiles: $profiles)..."
    Push-Location $root
    try {
        & .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=$profiles"
    } finally {
        Pop-Location
    }
} finally {
    if ($keycloak -and -not $keycloak.HasExited) {
        Write-Host "Stopping Keycloak..."
        # kc.bat starts java as a child process, so stop the whole tree.
        & taskkill /PID $keycloak.Id /T /F | Out-Null
    }
}
