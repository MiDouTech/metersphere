# Load before starting backend (PowerShell: . .\dev\env.ps1)

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$env:MS_CONFIG_DIR = Join-Path $ProjectRoot "local-runtime\conf"
$env:MS_LOG_PATH = Join-Path $ProjectRoot "local-runtime\logs\metersphere"
$env:MS_REDISSON_CONFIG = "file:$($env:MS_CONFIG_DIR -replace '\\','/')/redisson.yml"
$env:JMETER_HOME = Join-Path $ProjectRoot "local-runtime\jmeter"

Write-Host "MeterSphere local env loaded."
Write-Host "  MS_CONFIG_DIR = $env:MS_CONFIG_DIR"
Write-Host "  Config source = local-runtime/conf"
