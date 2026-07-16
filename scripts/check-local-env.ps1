$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ok = $true

function Test-Port {
    param([string]$Name, [int]$Port)
    $conn = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue
    if ($conn.TcpTestSucceeded) {
        Write-Host "[OK]   $Name (:$Port)"
    } else {
        Write-Host "[FAIL] $Name (:$Port)"
        $script:ok = $false
    }
}

function Test-Http {
    param([string]$Name, [string]$Url)
    try {
        $resp = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
        if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
            Write-Host "[OK]   $Name ($Url)"
        } else {
            Write-Host "[FAIL] $Name ($Url) status=$($resp.StatusCode)"
            $script:ok = $false
        }
    } catch {
        Write-Host "[FAIL] $Name ($Url) - $($_.Exception.Message)"
        $script:ok = $false
    }
}

Write-Host "=== Docker containers ==="
docker ps --filter "name=ms-dev-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

Write-Host ""
Write-Host "=== Port checks ==="
Test-Port "MySQL" 3306
Test-Port "Redis" 6379
Test-Port "Kafka" 9092
Test-Port "MinIO" 9000
Test-Port "Nacos" 8848
Test-Port "Backend" 8081
Test-Port "Frontend" 5173

Write-Host ""
Write-Host "=== HTTP checks ==="
Test-Http "Nacos console" "http://127.0.0.1:8848/nacos"
Test-Http "MinIO health" "http://127.0.0.1:9000/minio/health/live"

Write-Host ""
Write-Host "=== Local files ==="
$files = @(
    (Join-Path $ProjectRoot "local-runtime\conf\metersphere.properties"),
    (Join-Path $ProjectRoot "local-runtime\conf\redisson.yml"),
    (Join-Path $ProjectRoot "deploy\nacos\dev\metersphere.properties")
)
foreach ($f in $files) {
    if (Test-Path $f) { Write-Host "[OK]   $f" } else { Write-Host "[MISS] $f"; $ok = $false }
}

Write-Host ""
if ($ok) { Write-Host "Environment check passed." } else { Write-Host "Some checks failed. Run scripts\setup-local-env.ps1 and scripts\start-local-deps.ps1" }
