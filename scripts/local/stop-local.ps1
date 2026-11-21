# Stops a Keycloak left running by run-local.ps1 (for example after closing its window).
$found = Get-CimInstance Win32_Process |
    Where-Object { $_.CommandLine -match '\\\.local\\keycloak-[^\\]+\\' -and $_.Name -in 'java.exe', 'cmd.exe' }
foreach ($p in $found) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
Write-Host ("Stopped {0} Keycloak process(es)." -f @($found).Count)
