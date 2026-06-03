# Start middleware (Docker) and initialize MySQL tables — no DataGrip.
$ErrorActionPreference = "Stop"
# docker/mysqladmin writes warnings to stderr; do not treat as script failure (Windows PowerShell)
if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$CoreServices = @("mysql", "redis", "minio", "rmqnamesrv", "rmqbroker")

Write-Host "Starting Docker services: $($CoreServices -join ', ')" -ForegroundColor Cyan
docker compose up -d @CoreServices
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Docker failed to pull/start images." -ForegroundColor Red
    Write-Host "If you see registry-1.docker.io timeout, configure Docker mirror or VPN." -ForegroundColor Yellow
    exit $LASTEXITCODE
}

Write-Host "Waiting for MySQL..." -ForegroundColor Cyan
$ready = $false
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
for ($i = 0; $i -lt 30; $i++) {
    docker exec mysql-media mysqladmin ping -uroot -proot --silent *>$null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
$ErrorActionPreference = $prevEap
if (-not $ready) {
    Write-Host "MySQL did not become ready in time." -ForegroundColor Red
    exit 1
}

& "$PSScriptRoot\init-db.ps1"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Green
Write-Host "  1. Backend:  cd server; mvn spring-boot:run"
Write-Host "  2. Frontend: cd client; npm install; npm run dev"
Write-Host "  3. Open:     http://localhost:5173"
