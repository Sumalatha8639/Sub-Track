<#
.SYNOPSIS
  Downloads portable MySQL 8.4 and Mailpit into .tools\ (no installer, no admin rights, no Docker).
  Run once on a new PC; dev-services.ps1 setup also calls it automatically when the tools are missing.

.USAGE
  .\scripts\install-tools.ps1
#>
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster
$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root '.tools'
$mysqlVersion = '8.4.13'

New-Item -ItemType Directory -Force $tools | Out-Null

function Get-Zip([string[]]$urls, [string]$target) {
    foreach ($url in $urls) {
        try {
            Write-Host "Downloading $url ..."
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $target
            return
        } catch {
            Write-Warning "Failed: $($_.Exception.Message)"
        }
    }
    throw "Could not download $target from any mirror."
}

# ---- MySQL --------------------------------------------------------------------
$mysqlDir = Join-Path $tools 'mysql'
if (Test-Path (Join-Path $mysqlDir 'bin\mysqld.exe')) {
    Write-Host 'MySQL already present.'
} else {
    $tmp = Join-Path $tools 'dl-mysql'
    New-Item -ItemType Directory -Force $tmp | Out-Null
    $zip = Join-Path $tmp 'mysql.zip'
    # The current release lives on the CDN; older ones move to the archive
    Get-Zip @(
        "https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$mysqlVersion-winx64.zip",
        "https://downloads.mysql.com/archives/get/p/23/file/mysql-$mysqlVersion-winx64.zip"
    ) $zip
    Write-Host 'Extracting MySQL (about 1 GB unpacked, takes a minute)...'
    Expand-Archive -Force $zip (Join-Path $tmp 'x')
    Move-Item (Join-Path $tmp "x\mysql-$mysqlVersion-winx64") $mysqlDir
    Remove-Item -Recurse -Force $tmp
    Write-Host 'MySQL ready.'
}

# ---- Mailpit ------------------------------------------------------------------
$mailpitDir = Join-Path $tools 'mailpit'
if (Test-Path (Join-Path $mailpitDir 'mailpit.exe')) {
    Write-Host 'Mailpit already present.'
} else {
    $zip = Join-Path $tools 'mailpit.zip'
    Get-Zip @('https://github.com/axllent/mailpit/releases/latest/download/mailpit-windows-amd64.zip') $zip
    Expand-Archive -Force $zip $mailpitDir
    Remove-Item -Force $zip
    Write-Host 'Mailpit ready.'
}

Write-Host ''
Write-Host 'Tools installed in .tools\. Next: .\scripts\dev-services.ps1 setup'
