<#
.SYNOPSIS
  Packs the project into a small zip to share: source code only.
  Leaves out node_modules, build output, the local MySQL/Mailpit tools and data, logs and screenshots;
  the other PC recreates them with install-tools.ps1, npm install and Maven.

.USAGE
  .\scripts\make-zip.ps1                      # creates ..\SubTrack.zip next to the project folder
  .\scripts\make-zip.ps1 -Out C:\temp\SubTrack.zip
#>
param([string]$Out)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Out) { $Out = Join-Path (Split-Path -Parent $root) 'SubTrack.zip' }

$stage = Join-Path $env:TEMP "subtrack-zip-$(Get-Random)"
$dest = Join-Path $stage 'SubTrack'

# robocopy: copy everything except heavy or machine-specific folders and files
$excludeDirs = 'node_modules', 'target', 'dist', '.tools', '.playwright-mcp', '.git', '.idea', '.vscode'
$excludeFiles = '*.log', 'SubTrack_Interview_Guide.pdf', '.env'
robocopy $root $dest /E /NFL /NDL /NJH /NJS /NP /XD $excludeDirs /XF $excludeFiles | Out-Null
if ($LASTEXITCODE -ge 8) { throw "Copy failed (robocopy exit code $LASTEXITCODE)" }

if (Test-Path $Out) { Remove-Item -Force $Out }
Compress-Archive -Path $dest -DestinationPath $Out
Remove-Item -Recurse -Force $stage

$size = [math]::Round((Get-Item $Out).Length / 1MB, 1)
Write-Host "Created $Out ($size MB)"
exit 0   # robocopy leaves a non-zero code even on success
