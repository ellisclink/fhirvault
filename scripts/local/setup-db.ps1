# Creates the fhirvault role and database in a locally installed PostgreSQL (no Docker).
# Run once: powershell -ExecutionPolicy Bypass -File scripts\local\setup-db.ps1
# psql asks for the postgres superuser password; the fhirvault password comes from deploy\.env.
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
. (Join-Path $PSScriptRoot "env.ps1")
Import-DotEnv (Join-Path $root "deploy\.env")

$psql = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
    Sort-Object { [int]($_.Directory.Parent.Name -replace '\D', '') } -Descending | Select-Object -First 1
if (-not $psql) { throw "psql.exe not found under C:\Program Files\PostgreSQL. Install PostgreSQL 16 or newer." }

$password = $env:POSTGRES_PASSWORD -replace "'", "''"
$sql = @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fhirvault') THEN
    CREATE ROLE fhirvault LOGIN PASSWORD '$password';
  ELSE
    ALTER ROLE fhirvault WITH LOGIN PASSWORD '$password';
  END IF;
END
`$`$;
SELECT 'CREATE DATABASE fhirvault OWNER fhirvault' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fhirvault')\gexec
"@

# The SQL goes in on stdin so the password never appears in the process list.
$sql | & $psql.FullName -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -q
if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
Write-Host "Database 'fhirvault' and role 'fhirvault' are ready on localhost:5432."
