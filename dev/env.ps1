# Load before starting backend (PowerShell: . .\dev\env.ps1)

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$env:MS_CONFIG_DIR = Join-Path $ProjectRoot "local-runtime\conf"
$env:MS_LOG_PATH = Join-Path $ProjectRoot "local-runtime\logs\metersphere"
$env:MS_REDISSON_CONFIG = "file:$($env:MS_CONFIG_DIR -replace '\\','/')/redisson.yml"
$env:JMETER_HOME = Join-Path $ProjectRoot "local-runtime\jmeter"

# Nacos (only when -UseNacos; local profile uses local-runtime/conf instead)
# $env:NACOS_SERVER_ADDR = "127.0.0.1:8848"
# $env:NACOS_NAMESPACE = "dev"
# $env:NACOS_GROUP = "METERSPHERE"

Write-Host "MeterSphere local env loaded."
Write-Host "  MS_CONFIG_DIR = $env:MS_CONFIG_DIR"
Write-Host "  Config source = local-runtime/conf (use start.ps1 -UseNacos for Nacos)"
