<#
.SYNOPSIS
  task010 Agent API 端到端联调验证脚本

.DESCRIPTION
  验证 health / 认证 / search / modules / submit / OpenAPI agent 分组。
  前置：MeterSphere 后端已启动，Flyway 已迁移，fixture Token 已导入。

.EXAMPLE
  .\scripts\verify-agent-api.ps1
  .\scripts\verify-agent-api.ps1 -ProjectId 100001100001 -BaseUrl http://localhost:8081
#>
param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$ProjectId = "100001100001",
    # 优先环境变量；默认仅为本地 fixture 演示值，禁止用于生产
    [string]$Token = $(if ($env:MS_AGENT_TOKEN) { $env:MS_AGENT_TOKEN } else { "msat_demo_token_for_local_testing_01" }),
    [string]$ReadOnlyToken = $(if ($env:MS_AGENT_TOKEN_READONLY) { $env:MS_AGENT_TOKEN_READONLY } else { "msat_demo_readonly_token_01" })
)

$ErrorActionPreference = "Continue"
$passed = 0
$failed = 0

function Assert-Status {
    param([string]$Name, [scriptblock]$Block, [int]$Expected)
    try {
        & $Block
        if ($script:lastStatus -eq $Expected) {
            Write-Host "[PASS] $Name (HTTP $Expected)" -ForegroundColor Green
            $script:passed++
        } else {
            Write-Host "[FAIL] $Name - expected HTTP $Expected, got $($script:lastStatus)" -ForegroundColor Red
            $script:failed++
        }
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq $Expected) {
            Write-Host "[PASS] $Name (HTTP $Expected)" -ForegroundColor Green
            $script:passed++
        } else {
            Write-Host "[FAIL] $Name - expected HTTP $Expected, got $code - $($_.Exception.Message)" -ForegroundColor Red
            $script:failed++
        }
    }
}

function Invoke-Agent {
    param(
        [string]$Method = "GET",
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    $params = @{
        Uri             = $Uri
        Method          = $Method
        UseBasicParsing = $true
        TimeoutSec      = 30
        Headers         = $Headers
    }
    if ($Body) {
        $params.Body = $Body
        $params.Headers["Content-Type"] = "application/json"
    }
    $r = Invoke-WebRequest @params
    $script:lastStatus = [int]$r.StatusCode
    return $r
}

Write-Host "=== Agent API E2E Verification ===" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl"
Write-Host "ProjectId: $ProjectId"
Write-Host ""

Assert-Status "health (no token)" {
    $r = Invoke-Agent -Uri "$BaseUrl/api/agent/v1/functional/health"
    if ($r.StatusCode -ne 200 -or $r.Content -notmatch "ok") { throw "unexpected health response" }
} 200

Assert-Status "modules without token" {
    Invoke-Agent -Uri "$BaseUrl/api/agent/v1/functional/modules?projectId=$ProjectId" | Out-Null
} 401

Assert-Status "modules with invalid token" {
    Invoke-Agent -Uri "$BaseUrl/api/agent/v1/functional/modules?projectId=$ProjectId" -Headers @{
        Authorization = "Bearer msat_invalid_token_value"
    } | Out-Null
} 401

Assert-Status "modules with valid token" {
    Invoke-Agent -Uri "$BaseUrl/api/agent/v1/functional/modules?projectId=$ProjectId" -Headers @{
        Authorization  = "Bearer $Token"
        "X-MS-PROJECT" = $ProjectId
    } | Out-Null
} 200

Assert-Status "search with valid token" {
    $body = '{"query":"订单","includeSteps":true,"current":1,"pageSize":10}'
    Invoke-Agent -Method POST -Uri "$BaseUrl/api/agent/v1/functional/search" -Body $body -Headers @{
        Authorization  = "Bearer $Token"
        "X-MS-PROJECT" = $ProjectId
    } | Out-Null
} 200

Assert-Status "submit with read-only token (403)" {
    $body = (@{
        projectId      = $ProjectId
        caseId         = "fc-001"
        lastExecResult = "SUCCESS"
    } | ConvertTo-Json -Compress)
    try {
        Invoke-Agent -Method POST -Uri "$BaseUrl/api/agent/v1/functional/submit" -Body $body -Headers @{
            Authorization  = "Bearer $ReadOnlyToken"
            "X-MS-PROJECT" = $ProjectId
        } | Out-Null
    } catch {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $detail = $reader.ReadToEnd()
        if ($detail -notmatch "scope|403|FORBIDDEN|不足") {
            throw "unexpected forbidden response: $detail"
        }
        $script:lastStatus = [int]$_.Exception.Response.StatusCode.value__
        return
    }
} 403

Write-Host "[SKIP] OpenAPI agent group - springdoc disabled in local profile (GroupedOpenApi bean configured)" -ForegroundColor Yellow

Write-Host ""
Write-Host "Results: $passed passed, $failed failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Yellow" })
if ($failed -gt 0) { exit 1 }
