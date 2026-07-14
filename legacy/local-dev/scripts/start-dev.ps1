param(
    [switch]$Stop,
    [switch]$BackendOnly,
    [switch]$FrontendOnly
)

$ErrorActionPreference = "Stop"
$ArchiveRoot = Split-Path -Parent $PSScriptRoot
$ProjectRoot = Split-Path -Parent $ArchiveRoot
$PidFile = Join-Path $ProjectRoot "local-runtime\dev.pids"

function Stop-DevProcesses {
    if (-not (Test-Path $PidFile)) {
        Write-Host "No dev PID file found."
        return
    }
    Get-Content $PidFile | ForEach-Object {
        if ($_ -match '^(backend|frontend):(\d+)$') {
            $procId = [int]$Matches[2]
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($proc) {
                Stop-Process -Id $procId -Force
                Write-Host "Stopped $($Matches[1]) (PID $procId)"
            }
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

if ($Stop) {
    Stop-DevProcesses
    exit 0
}

. (Join-Path $ArchiveRoot "dev\env.ps1")

$staticDir = Join-Path $ProjectRoot "backend\app\src\main\resources\static"
$publicDir = Join-Path $ProjectRoot "frontend\public"
if (Test-Path $publicDir) {
    New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
    Copy-Item -Recurse -Force (Join-Path $publicDir "*") $staticDir
    Write-Host "Synced frontend/public to backend static resources."
}

$pids = @()

if (-not $FrontendOnly) {
    Write-Host "Starting backend on port 8081..."
    $backend = Start-Process -FilePath (Join-Path $ProjectRoot "mvnw.cmd") -ArgumentList @(
        "-pl", "backend/app", "-am",
        "spring-boot:run",
        "-DskipTests", "-DskipAntRunForJenkins=true"
    ) -WorkingDirectory $ProjectRoot -PassThru -WindowStyle Hidden
    $pids += "backend:$($backend.Id)"
    Write-Host "Backend PID: $($backend.Id)"
}

if (-not $BackendOnly) {
    Write-Host "Starting frontend on port 5173..."
    $frontend = Start-Process -FilePath "npm" -ArgumentList @("run", "dev") `
        -WorkingDirectory (Join-Path $ProjectRoot "frontend") -PassThru -WindowStyle Hidden
    $pids += "frontend:$($frontend.Id)"
    Write-Host "Frontend PID: $($frontend.Id)"
}

$pids | Set-Content $PidFile
Write-Host ""
Write-Host "Dev servers started. Stop with: legacy\local-dev\scripts\start-dev.ps1 -Stop"
Write-Host "  Backend : http://localhost:8081"
Write-Host "  Frontend: http://127.0.0.1:5173"
