<#
.SYNOPSIS
  Starts everything for a SubTrack demo on this PC and opens the browser.

.USAGE
  .\scripts\start-demo.ps1            # MySQL + Mailpit, backend (with demo data), frontend
  .\scripts\start-demo.ps1 -NoDemo    # same, without the demo profile (your own data only)

  The backend and frontend open in their own windows; close those windows to stop them.
  Stop MySQL and Mailpit afterwards with: .\scripts\dev-services.ps1 stop
  Demo login: demo@subtrack.app / demo-password
#>
param([switch]$NoDemo)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Wait-Http([string]$url, [int]$seconds) {
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2 | Out-Null
            return $true
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    return $false
}

# 1. Database + mail catcher
& (Join-Path $PSScriptRoot 'dev-services.ps1') start

# 2. Backend
$profiles = if ($NoDemo) { 'local' } else { 'local,demo' }
$backend = Join-Path $root 'backend'
Start-Process powershell -WorkingDirectory $backend -ArgumentList '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'SubTrack backend'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=$profiles'"

# 3. Frontend
$frontend = Join-Path $root 'frontend'
if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    Write-Host 'Installing frontend packages (first run only)...'
    Push-Location $frontend
    npm install
    Pop-Location
}
Start-Process powershell -WorkingDirectory $frontend -ArgumentList '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'SubTrack frontend'; npm run dev"

# 4. Wait until both answer, then open the app and the mailbox
Write-Host 'Waiting for the backend (the first start can take a minute)...'
if (-not (Wait-Http 'http://localhost:8080/actuator/health' 180)) {
    Write-Warning 'Backend did not start in 3 minutes; check the "SubTrack backend" window.'
    return
}
if (-not (Wait-Http 'http://localhost:5173' 60)) {
    Write-Warning 'Frontend did not start; check the "SubTrack frontend" window.'
    return
}
Start-Process 'http://localhost:5173'
Start-Process 'http://localhost:8025'
Write-Host ''
Write-Host 'SubTrack is running:  http://localhost:5173'
Write-Host 'Emails (Mailpit):     http://localhost:8025'
if (-not $NoDemo) { Write-Host 'Demo login:           demo@subtrack.app / demo-password' }
Write-Host 'Send due reminders now: .\scripts\run-jobs.ps1'
