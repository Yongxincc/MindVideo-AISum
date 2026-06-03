$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ClientDir = Join-Path $Root "client"

Set-Location $ClientDir

if (-not (Test-Path "node_modules")) {
    Write-Host "First run: npm install ..." -ForegroundColor Cyan
    npm install
    if ($LASTEXITCODE -ne 0) {
        Read-Host "Press Enter to close"
        exit $LASTEXITCODE
    }
}

Write-Host "MindVideo frontend (http://localhost:5173)" -ForegroundColor Cyan
npm run dev
