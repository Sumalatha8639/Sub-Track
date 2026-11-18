<#
.SYNOPSIS
  Runs SubTrack's local services without Docker: portable MySQL 8.4 and Mailpit.

.USAGE
  .\scripts\dev-services.ps1 setup    # first time only: initialise MySQL, create databases and user
  .\scripts\dev-services.ps1 start    # start MySQL (3306) and Mailpit (SMTP 1025, UI http://localhost:8025)
  .\scripts\dev-services.ps1 stop     # stop both
  .\scripts\dev-services.ps1 status   # show what is running

  Binaries live in .tools\ (git-ignored). MySQL data lives in .tools\mysql-data.
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('setup', 'start', 'stop', 'status')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
$root     = Split-Path -Parent $PSScriptRoot
$tools    = Join-Path $root '.tools'
$mysqlDir = Join-Path $tools 'mysql'
$dataDir  = Join-Path $tools 'mysql-data'
$logDir   = Join-Path $tools 'logs'
$mysqld   = Join-Path $mysqlDir 'bin\mysqld.exe'
$mysql    = Join-Path $mysqlDir 'bin\mysql.exe'
$mysqladm = Join-Path $mysqlDir 'bin\mysqladmin.exe'
$mailpit  = Join-Path $tools 'mailpit\mailpit.exe'
$myIni    = Join-Path $tools 'my.ini'

function Test-Port([int]$port) {
    [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Write-MyIni {
    # Forward slashes keep MySQL happy with paths that contain spaces
    $base = $mysqlDir.Replace('\', '/')
    $data = $dataDir.Replace('\', '/')
    @"
[mysqld]
basedir="$base"
datadir="$data"
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone='+00:00'
"@ | Set-Content -Encoding ascii $myIni
}

function Start-MySql {
    if (Test-Port 3306) { Write-Host 'MySQL already listening on 3306'; return }
    New-Item -ItemType Directory -Force $logDir | Out-Null
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=`"$myIni`"", '--console' `
        -WindowStyle Hidden `
        -RedirectStandardError (Join-Path $logDir 'mysql.log') `
        -RedirectStandardOutput (Join-Path $logDir 'mysql.out.log')
    for ($i = 0; $i -lt 30 -and -not (Test-Port 3306); $i++) { Start-Sleep -Seconds 1 }
    if (Test-Port 3306) { Write-Host 'MySQL started on 3306' }
    else { throw "MySQL did not start; see $logDir\mysql.log" }
}

function Start-Mailpit {
    if (Test-Port 1025) { Write-Host 'Mailpit already listening on 1025'; return }
    New-Item -ItemType Directory -Force $logDir | Out-Null
    Start-Process -FilePath $mailpit -ArgumentList '--smtp', '127.0.0.1:1025', '--listen', '127.0.0.1:8025' `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'mailpit.log') `
        -RedirectStandardError (Join-Path $logDir 'mailpit.err.log')
    Start-Sleep -Seconds 2
    Write-Host 'Mailpit started: SMTP 1025, UI http://localhost:8025'
}

switch ($Action) {
    'setup' {
        if (-not (Test-Path $mysqld) -or -not (Test-Path $mailpit)) {
            Write-Host 'MySQL / Mailpit not found in .tools; downloading them first...'
            & (Join-Path $PSScriptRoot 'install-tools.ps1')
        }
        Write-MyIni
        if (Test-Path $dataDir) {
            Write-Host 'MySQL data directory already exists; skipping initialise.'
        } else {
            Write-Host 'Initialising MySQL data directory (root has no password, localhost only)...'
            & $mysqld "--defaults-file=$myIni" --initialize-insecure --console
        }
        Start-MySql
        $sql = @"
CREATE DATABASE IF NOT EXISTS subtrack CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS subtrack_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'subtrack'@'localhost' IDENTIFIED BY 'subtrack';
GRANT ALL PRIVILEGES ON subtrack.* TO 'subtrack'@'localhost';
GRANT ALL PRIVILEGES ON subtrack_test.* TO 'subtrack'@'localhost';
FLUSH PRIVILEGES;
"@
        $sql | & $mysql -u root --protocol=TCP -h 127.0.0.1 -P 3306
        Write-Host 'Databases subtrack and subtrack_test ready (user subtrack / subtrack).'
    }
    'start' {
        if (-not (Test-Path $dataDir)) { throw 'Run setup first: .\scripts\dev-services.ps1 setup' }
        Write-MyIni
        Start-MySql
        Start-Mailpit
    }
    'stop' {
        if (Test-Port 3306) {
            & $mysqladm -u root --protocol=TCP -h 127.0.0.1 -P 3306 shutdown
            Write-Host 'MySQL stopped'
        }
        Get-Process mailpit -ErrorAction SilentlyContinue | Stop-Process
        Write-Host 'Mailpit stopped'
    }
    'status' {
        'MySQL   (3306): ' + $(if (Test-Port 3306) { 'running' } else { 'stopped' })
        'Mailpit (1025): ' + $(if (Test-Port 1025) { 'running' } else { 'stopped' })
    }
}
