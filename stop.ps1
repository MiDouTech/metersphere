<#
.SYNOPSIS
  MeterSphere 本地一键停止脚本（chenqifen 分支）

.DESCRIPTION
  停止前端、后端，并可选停止 Docker 中间件（MySQL / Redis / Kafka / MinIO / Nacos）

.EXAMPLE
  .\stop.ps1              # 停止前后端 + Docker 中间件
  .\stop.ps1 -KeepDeps    # 仅停止前后端，保留中间件
#>
param(
    [switch]$KeepDeps
)

$ErrorActionPreference = "Continue"
$ProjectRoot = $PSScriptRoot
$ComposeFile = Join-Path $ProjectRoot "dev\docker-compose.yml"
$PidFile = Join-Path $ProjectRoot "local-runtime\dev.pids"
$DevPorts = @(8081, 5173, 7071)

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Stop-ProcessTree([int]$ProcessId, [string]$Label) {
    if ($ProcessId -le 0) { return $false }
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $proc) { return $false }
    taskkill /PID $ProcessId /T /F 2>$null | Out-Null
    Write-Host "  Stopped $Label (PID $ProcessId)"
    return $true
}

function Stop-PortListeners {
    param([int[]]$Ports)
    $any = $false
    foreach ($port in $Ports) {
        $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $conns) {
            if ($conn.OwningProcess -gt 0) {
                if (Stop-ProcessTree -ProcessId $conn.OwningProcess -Label "port $port listener") {
                    $any = $true
                }
            }
        }
    }
    return $any
}

function Stop-DevProcesses {
    Write-Step "Stopping frontend and backend"
    $stopped = $false

    if (Test-Path $PidFile) {
        Get-Content $PidFile | ForEach-Object {
            if ($_ -match '^(backend|frontend):(\d+)$') {
                if (Stop-ProcessTree -ProcessId ([int]$Matches[2]) -Label $Matches[1]) {
                    $stopped = $true
                }
            }
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }

    if (Stop-PortListeners -Ports $DevPorts) { $stopped = $true }

    Start-Sleep -Seconds 1
    $remaining = @()
    foreach ($port in $DevPorts) {
        if ((Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded) {
            $remaining += $port
        }
    }
    if ($remaining.Count -gt 0) {
        Write-Host "  Warning: ports still in use: $($remaining -join ', ')" -ForegroundColor Yellow
    } elseif ($stopped) {
        Write-Host "  Dev servers stopped." -ForegroundColor Green
    } else {
        Write-Host "  No dev servers were running." -ForegroundColor Yellow
    }
}

function Stop-DockerDeps {
    if ($KeepDeps) {
        Write-Step "Keeping Docker middleware (-KeepDeps)"
        return
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Step "Docker not found, skip middleware stop"
        return
    }
    Write-Step "Stopping Docker middleware"
    try {
        docker compose -f $ComposeFile down 2>&1 | ForEach-Object { Write-Host "  $_" }
        Write-Host "  Docker middleware stopped." -ForegroundColor Green
    } catch {
        Write-Host "  Docker stop failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "MeterSphere Local Stop" -ForegroundColor Green
Write-Host "Project: $ProjectRoot"

Stop-DevProcesses
Stop-DockerDeps

Write-Step "Shutdown complete"
Write-Host "  Restart: .\start.cmd"
Write-Host "  Restart without Docker: .\start.ps1 -SkipDeps"
