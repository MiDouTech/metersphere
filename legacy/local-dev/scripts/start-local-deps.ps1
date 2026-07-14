param(
    [switch]$Stop
)

$ErrorActionPreference = "Stop"
$ArchiveRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $ArchiveRoot "dev\docker-compose.yml"

if ($Stop) {
    Write-Host "Stopping local dependencies..."
    docker compose -f $ComposeFile down
    exit 0
}

Write-Host "Starting local dependencies (MySQL, Redis, Kafka, MinIO, Nacos)..."
docker compose -f $ComposeFile up -d

Write-Host "Waiting for services to become healthy..."
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    $ps = docker compose -f $ComposeFile ps --format json | ConvertFrom-Json
    $unhealthy = @($ps | Where-Object { $_.Health -and $_.Health -ne "healthy" })
    if ($unhealthy.Count -eq 0) {
        Write-Host "All services are healthy."
        break
    }
    Start-Sleep -Seconds 5
}

Write-Host ""
Write-Host "Services:"
Write-Host "  MySQL  : localhost:3306 (root / Password123@mysql, db=metersphere)"
Write-Host "  Redis  : localhost:6379 (Password123@redis)"
Write-Host "  Kafka  : localhost:9092"
Write-Host "  MinIO  : http://localhost:9000 (admin / Password123@minio, console :9001)"
Write-Host "  Nacos  : http://localhost:8848/nacos (nacos / nacos)"
