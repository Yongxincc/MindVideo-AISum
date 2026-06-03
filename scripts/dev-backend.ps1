$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ServerDir = Join-Path $Root "server"
$Props = Join-Path $ServerDir "src\main\resources\application.properties"

Set-Location $ServerDir

if (-not (Test-Path $Props)) {
    Write-Host "Missing application.properties" -ForegroundColor Red
    Write-Host "Copy from application.properties.example and fill in API keys / paths:" -ForegroundColor Yellow
    Write-Host "  server\src\main\resources\application.properties.example" -ForegroundColor Yellow
    Read-Host "Press Enter to close"
    exit 1
}

Write-Host "MindVideo backend (port 9090)" -ForegroundColor Cyan
mvn spring-boot:run
