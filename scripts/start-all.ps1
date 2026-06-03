<#
.SYNOPSIS
  One-shot dev startup: Docker middleware + backend + frontend in separate windows.

.PARAMETER SkipDocker
  Skip middleware (MySQL/Redis/MinIO/RocketMQ). Use when Docker is already up.

.PARAMETER MiddlewareOnly
  Only run scripts/start-dev.ps1 (same as before), do not open app terminals.

.EXAMPLE
  .\scripts\start-all.ps1

.EXAMPLE
  .\scripts\start-all.ps1 -SkipDocker
#>
param(
    [switch]$SkipDocker,
    [switch]$MiddlewareOnly
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Get-ShellExe {
    if (Get-Command pwsh -ErrorAction SilentlyContinue) { return "pwsh" }
    return "powershell"
}

function Start-DevWindow {
    param(
        [string]$Title,
        [string]$ScriptPath
    )
    $shell = Get-ShellExe
    $arg = "-NoExit -NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`""
    Start-Process $shell -ArgumentList $arg -WorkingDirectory $Root
    Write-Host "  -> $Title" -ForegroundColor DarkGray
}

if (-not $SkipDocker) {
    & "$PSScriptRoot\start-dev.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} else {
    Write-Host "Skipping Docker (SkipDocker)." -ForegroundColor DarkGray
}

if ($MiddlewareOnly) {
    exit 0
}

$Props = Join-Path $Root "server\src\main\resources\application.properties"
if (-not (Test-Path $Props)) {
    Write-Host ""
    Write-Host "Warning: server application.properties not found." -ForegroundColor Yellow
    Write-Host "Copy application.properties.example before the backend can start." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Opening backend and frontend terminals ..." -ForegroundColor Cyan
Start-DevWindow -Title "Backend (Maven)" -ScriptPath "$PSScriptRoot\dev-backend.ps1"
Start-Sleep -Milliseconds 400
Start-DevWindow -Title "Frontend (Vite)" -ScriptPath "$PSScriptRoot\dev-frontend.ps1"

Write-Host ""
Write-Host "Done. Open http://localhost:5173 when Vite is ready." -ForegroundColor Green
Write-Host "Close the two dev windows to stop backend/frontend." -ForegroundColor Green
