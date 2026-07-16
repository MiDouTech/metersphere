$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ConfigTemplate = Join-Path $ProjectRoot "deploy\nacos\dev\metersphere.properties"
$RedissonTemplate = Join-Path $ProjectRoot "local-runtime\conf\redisson.yml"
$RuntimeConfDir = Join-Path $ProjectRoot "local-runtime\conf"
$RuntimeLogDir = Join-Path $ProjectRoot "local-runtime\logs\metersphere"
$RuntimeJmeterDir = Join-Path $ProjectRoot "local-runtime\jmeter"

New-Item -ItemType Directory -Force -Path $RuntimeConfDir, $RuntimeLogDir, $RuntimeJmeterDir | Out-Null

function Sync-BackendStaticAssets {
    $staticDir = Join-Path $ProjectRoot "backend\app\src\main\resources\static"
    $publicDir = Join-Path $ProjectRoot "frontend\public"
    if (-not (Test-Path $publicDir)) {
        Write-Warning "frontend/public not found, skip static asset sync."
        return
    }
    New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
    Copy-Item -Recurse -Force (Join-Path $publicDir "*") $staticDir
    Write-Host "Synced frontend/public to backend static resources."
}

Sync-BackendStaticAssets

if (-not (Test-Path $RedissonTemplate)) {
    @"
singleServerConfig:
  address: "redis://127.0.0.1:6379"
  password: "Password123@redis"
  database: 1
"@ | Set-Content -Path (Join-Path $RuntimeConfDir "redisson.yml") -Encoding UTF8
}

Copy-Item -Force $ConfigTemplate (Join-Path $RuntimeConfDir "metersphere.properties")

Write-Host "Local runtime directories initialized."
Write-Host "Seeding Nacos config..."
& (Join-Path $PSScriptRoot "seed-nacos-config.ps1")
