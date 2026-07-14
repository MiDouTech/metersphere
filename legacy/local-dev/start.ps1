<#
.SYNOPSIS
  [ARCHIVED] MeterSphere 本地一键启动脚本 — 已迁至 legacy/local-dev

.DESCRIPTION
  团队默认云开发，本脚本仅应急使用。日常请对接云端/线上实例，见 deploy/README.md。
  依次完成：环境检查 → Docker 中间件 → 本地配置 → Nacos 推送（可选）→ 后端 → 前端

.EXAMPLE
  .\legacy\local-dev\start.ps1              # 全量启动
  .\legacy\local-dev\start.ps1 -Stop        # 停止前后端
  .\legacy\local-dev\start.ps1 -SkipDeps    # 跳过 Docker
#>
param(
    [switch]$Stop,
    [switch]$SkipDeps,
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$UseNacos,
    [switch]$NoFrontendInstall
)

$ErrorActionPreference = "Stop"
# Archived under legacy/local-dev — repo root is two levels up
$ArchiveRoot = $PSScriptRoot
$ProjectRoot = (Resolve-Path (Join-Path $ArchiveRoot "..\..")).Path
$ComposeFile = Join-Path $ArchiveRoot "dev\docker-compose.yml"
$PidFile = Join-Path $ProjectRoot "local-runtime\dev.pids"
$BackendLog = Join-Path $ProjectRoot "local-runtime\logs\backend-startup.log"
$FrontendLog = Join-Path $ProjectRoot "local-runtime\logs\frontend-startup.log"
$RuntimeConfDir = Join-Path $ProjectRoot "local-runtime\conf"
$RuntimeLogDir = Join-Path $ProjectRoot "local-runtime\logs\metersphere"
$NacosDevConfig = Join-Path $ArchiveRoot "nacos\metersphere.properties"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-PortOpen([int]$Port, [string]$HostName = "127.0.0.1") {
    return (Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
}

function Stop-DevProcesses {
    Write-Step "Stopping dev processes"
    if (Test-Path $PidFile) {
        Get-Content $PidFile | ForEach-Object {
            if ($_ -match '^(backend|frontend):(\d+)$') {
                $procId = [int]$Matches[2]
                $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
                if ($proc) {
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    Write-Host "  Stopped $($Matches[1]) PID $procId"
                }
            }
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
    foreach ($port in @(8081, 5173)) {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) {
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "  Freed port $port (PID $($conn.OwningProcess))"
        }
    }
    Write-Host "Done."
}

function Ensure-Prerequisites {
    Write-Step "Checking prerequisites"
    if (-not (Test-CommandExists "java")) { throw "Java not found. Install JDK 21." }
    $javaVer = (cmd /c "java -version 2>&1" | Select-Object -First 1).ToString()
    Write-Host "  Java  : $javaVer"
    if (-not $BackendOnly) {
        if (-not (Test-CommandExists "npm")) { throw "npm not found. Install Node.js 20+." }
        Write-Host "  Node  : $(node -v)"
    }
    if (-not (Test-Path (Join-Path $ProjectRoot "mvnw.cmd"))) {
        throw "mvnw.cmd not found. Run from project root."
    }
}

function Start-DockerDeps {
    if ($SkipDeps) {
        Write-Step "Skipping Docker dependencies (-SkipDeps)"
        return
    }
    if (-not (Test-CommandExists "docker")) {
        Write-Host "  Docker not found. Assuming middleware is already running locally." -ForegroundColor Yellow
        return
    }
    Write-Step "Starting Docker middleware"
    try {
        docker info 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $dockerDesktop = "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe"
            if (Test-Path $dockerDesktop) {
                Write-Host "  Starting Docker Desktop..."
                Start-Process $dockerDesktop | Out-Null
                $deadline = (Get-Date).AddMinutes(3)
                while ((Get-Date) -lt $deadline) {
                    docker info 2>$null | Out-Null
                    if ($LASTEXITCODE -eq 0) { break }
                    Start-Sleep 3
                }
            }
        }
        docker compose -f $ComposeFile up -d
        Write-Host "  Waiting for middleware ports..."
        $ports = @{3306="MySQL"; 6379="Redis"; 8848="Nacos"; 9000="MinIO"; 9092="Kafka"}
        $deadline = (Get-Date).AddMinutes(4)
        while ((Get-Date) -lt $deadline) {
            $allReady = $true
            foreach ($entry in $ports.GetEnumerator()) {
                if (-not (Test-PortOpen $entry.Key)) { $allReady = $false; break }
            }
            if ($allReady) { break }
            Start-Sleep 5
        }
        foreach ($entry in $ports.GetEnumerator()) {
            $ok = Test-PortOpen $entry.Key
            $status = if ($ok) { "OK" } else { "NOT READY" }
            Write-Host "  $($entry.Value) (:$($entry.Key)) -> $status"
        }
    } catch {
        Write-Host "  Docker start failed: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "  Continue with existing local middleware if ports are open." -ForegroundColor Yellow
    }
}

function Initialize-LocalConfig {
    Write-Step "Initializing local config"
    New-Item -ItemType Directory -Force -Path $RuntimeConfDir, $RuntimeLogDir, (Join-Path $ProjectRoot "local-runtime\jmeter") | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $BackendLog) | Out-Null

    $redissonFile = Join-Path $RuntimeConfDir "redisson.yml"
    if (-not (Test-Path $redissonFile)) {
        @"
singleServerConfig:
  address: "redis://127.0.0.1:6379"
  password: "Password123@redis"
  database: 1
"@ | Set-Content -Path $redissonFile -Encoding UTF8
    }

    Copy-Item -Force $NacosDevConfig `
        (Join-Path $RuntimeConfDir "metersphere.properties")
    Write-Host "  Config: local-runtime\conf\metersphere.properties"
}

function Publish-NacosConfig {
    if (-not $UseNacos) {
        Write-Step "Using local profile (local-runtime/conf)"
        Write-Host "  Tip: add -UseNacos to load config from Nacos"
        return
    }
    $env:NACOS_SERVER_ADDR = if ($env:NACOS_SERVER_ADDR) { $env:NACOS_SERVER_ADDR } else { "127.0.0.1:8848" }
    $env:NACOS_NAMESPACE = if ($env:NACOS_NAMESPACE) { $env:NACOS_NAMESPACE } else { "dev" }
    $env:NACOS_GROUP = if ($env:NACOS_GROUP) { $env:NACOS_GROUP } else { "METERSPHERE" }
    Write-Step "Publishing config to Nacos"
    if (-not (Test-PortOpen 8848)) {
        Write-Host "  Nacos not reachable. Using local file fallback." -ForegroundColor Yellow
        return
    }
    try {
        $resp = Invoke-RestMethod -Uri "http://127.0.0.1:8848/nacos/v1/console/health/readiness" -TimeoutSec 5
        if ($resp -ne "OK") { throw "Nacos not ready" }
        $content = Get-Content $NacosDevConfig -Raw -Encoding UTF8
        $params = @{
            dataId   = "metersphere.properties"
            group    = $env:NACOS_GROUP
            tenant   = $env:NACOS_NAMESPACE
            type     = "properties"
            content  = $content
            username = $env:NACOS_USERNAME
            password = $env:NACOS_PASSWORD
        }
        Invoke-RestMethod -Uri "http://127.0.0.1:8848/nacos/v1/cs/configs" -Method Post -Body $params | Out-Null
        Write-Host "  Published metersphere.properties -> Nacos namespace=$($env:NACOS_NAMESPACE)"
    } catch {
        Write-Host "  Nacos publish failed: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "  Backend will use local-runtime/conf/metersphere.properties" -ForegroundColor Yellow
    }
}

function Wait-HttpReady([string]$Url, [int]$TimeoutSec = 300) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch {}
        Start-Sleep 5
    }
    return $false
}

function Start-Backend {
    Write-Step "Building backend (first run or code changes)"
    $mvnw = Join-Path $ProjectRoot "mvnw.cmd"
    & $mvnw -f (Join-Path $ProjectRoot "backend\pom.xml") install -pl app -am -DskipTests -DskipAntRunForJenkins=true | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Backend build failed. See Maven output above." }

    Write-Step "Starting backend (port 8081)"
    if (Test-PortOpen 8081) {
        Write-Host "  Port 8081 already in use. Skip backend start." -ForegroundColor Yellow
        return
    }
    $args = @(
        "-f", (Join-Path $ProjectRoot "backend\app\pom.xml"),
        "spring-boot:run",
        "-DskipTests", "-DskipAntRunForJenkins=true",
        "-Dspring-boot.run.jvmArguments=-Dnacos.logging.default.config.enabled=false"
    )
    if (-not $UseNacos) {
        $args += "-Dspring-boot.run.profiles=local"
    } else {
        $args += "-Dspring-boot.run.profiles=nacos"
    }
    $proc = Start-Process -FilePath $mvnw -ArgumentList $args `
        -WorkingDirectory $ProjectRoot -PassThru `
        -RedirectStandardOutput $BackendLog -RedirectStandardError "$BackendLog.err" `
        -WindowStyle Hidden
    Write-Host "  Backend PID: $($proc.Id)"
    Write-Host "  Log: local-runtime\logs\backend-startup.log"
    Write-Host "  Waiting for backend (up to 5 min)..."
    if (Wait-HttpReady "http://127.0.0.1:8081/is-login" 300) {
        Write-Host "  Backend is ready." -ForegroundColor Green
    } else {
        Write-Host "  Backend not ready yet. Check log:" -ForegroundColor Yellow
        Get-Content $BackendLog -Tail 30 -ErrorAction SilentlyContinue
    }
    return $proc.Id
}

function Ensure-FrontendDeps {
    if ($NoFrontendInstall) { return }
    $nodeModules = Join-Path $ProjectRoot "frontend\node_modules"
    if (-not (Test-Path $nodeModules)) {
        Write-Step "Installing frontend dependencies (first run, pnpm)"
        Push-Location (Join-Path $ProjectRoot "frontend")
        try {
            if (Test-CommandExists "pnpm") {
                pnpm install
            } else {
                npm install
            }
        } finally {
            Pop-Location
        }
    }
}

function Resolve-DevCommand([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }
    $path = $cmd.Source
    if ($path -match '\.(cmd|bat)$') { return $path }
    $cmdPath = Join-Path (Split-Path $path) "$Name.cmd"
    if (Test-Path $cmdPath) { return $cmdPath }
    return $path
}

function Start-Frontend {
    Write-Step "Starting frontend (port 5173)"
    if (Test-PortOpen 5173) {
        Write-Host "  Port 5173 already in use. Skip frontend start." -ForegroundColor Yellow
        return
    }
    Ensure-FrontendDeps
    $frontendDir = Join-Path $ProjectRoot "frontend"
    $pnpm = Resolve-DevCommand "pnpm"
    if ($pnpm) {
        $proc = Start-Process -FilePath $pnpm -ArgumentList @("run", "dev") `
            -WorkingDirectory $frontendDir -PassThru `
            -RedirectStandardOutput $FrontendLog -RedirectStandardError "$FrontendLog.err" `
            -WindowStyle Hidden
    } else {
        $npm = Resolve-DevCommand "npm"
        $proc = Start-Process -FilePath $npm -ArgumentList @("run", "dev") `
            -WorkingDirectory $frontendDir -PassThru `
            -RedirectStandardOutput $FrontendLog -RedirectStandardError "$FrontendLog.err" `
            -WindowStyle Hidden
    }
    Write-Host "  Frontend PID: $($proc.Id)"
    Write-Host "  Log: local-runtime\logs\frontend-startup.log"
    Write-Host "  Waiting for frontend..."
    if (Wait-HttpReady "http://localhost:5173" 120) {
        Write-Host "  Frontend is ready." -ForegroundColor Green
    } else {
        Write-Host "  Frontend starting... check log if page not loading." -ForegroundColor Yellow
    }
    return $proc.Id
}

# ---- main ----
if ($Stop) {
    Stop-DevProcesses
    if (-not $SkipDeps -and (Test-CommandExists "docker")) {
        Write-Step "Stopping Docker middleware"
        docker compose -f $ComposeFile down 2>$null
    }
    exit 0
}

Write-Host "MeterSphere Local Start" -ForegroundColor Green
Write-Host "Project: $ProjectRoot"

Ensure-Prerequisites
. (Join-Path $ArchiveRoot "dev\env.ps1")
Start-DockerDeps
Initialize-LocalConfig
Publish-NacosConfig

$pids = @()
if (-not $FrontendOnly) {
    $backendPid = Start-Backend
    if ($backendPid) { $pids += "backend:$backendPid" }
}
if (-not $BackendOnly) {
    $frontendPid = Start-Frontend
    if ($frontendPid) { $pids += "frontend:$frontendPid" }
}
if ($pids.Count -gt 0) {
    $pids | Set-Content $PidFile
}

Write-Step "Startup complete"
Write-Host "  Backend : http://localhost:8081"
Write-Host "  Frontend: http://localhost:5173"
Write-Host "  Nacos   : http://localhost:8848/nacos"
Write-Host ""
Write-Host "Stop all: .\legacy\local-dev\stop.cmd"
