# Loads KEY=VALUE lines from a .env file into this process's environment, without printing them.
function Import-DotEnv([string] $path) {
    if (-not (Test-Path $path)) { throw "Missing $path. Copy deploy\.env.example to deploy\.env and set the values." }
    foreach ($line in Get-Content $path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], "Process")
        }
    }
    foreach ($name in "POSTGRES_PASSWORD", "KEYCLOAK_ADMIN_PASSWORD", "FHIRVAULT_LOADER_CLIENT_SECRET", "FHIRVAULT_DEMO_PASSWORD") {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")
        if ([string]::IsNullOrWhiteSpace($value) -or $value -eq "change-me") { throw "Set $name in $path." }
    }
}
