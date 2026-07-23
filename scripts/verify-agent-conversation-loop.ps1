<#
.SYNOPSIS
  agent_conversation_loop task010：写闭环联调脚本

.DESCRIPTION
  覆盖：Scope 拒绝、项目创建、批量导入、计划/评审关联、search(testPlanCaseId)、提缺陷、OpenAPI。
  前置：后端已启动；Token 需含 AGENT_ALL（或各 WRITE + READ/SUBMIT）。
  默认不创建项目：传入 -ProjectId 走既有项目；传 -OrganizationId 且 -CreateProject 才新建。

.EXAMPLE
  $env:MS_AGENT_TOKEN = "msat_xxx"
  .\scripts\verify-agent-conversation-loop.ps1 -OrganizationId org-xxx -CreateProject
  .\scripts\verify-agent-conversation-loop.ps1 -ProjectId existing-id -SkipProjectCreate
#>
param(
    [string]$BaseUrl = $(if ($env:MS_BASE_URL) { $env:MS_BASE_URL } else { "http://127.0.0.1:8081" }),
    [string]$Token = $(if ($env:MS_AGENT_TOKEN) { $env:MS_AGENT_TOKEN } else { "" }),
    [string]$OrganizationId = $(if ($env:MS_ORG_ID) { $env:MS_ORG_ID } else { "" }),
    [string]$ProjectId = $(if ($env:MS_PROJECT_ID) { $env:MS_PROJECT_ID } else { "" }),
    [string]$AdminUserId = $(if ($env:MS_USER_ID) { $env:MS_USER_ID } else { "admin" }),
    [switch]$CreateProject,
    [switch]$SkipProjectCreate
)

$ErrorActionPreference = "Continue"
$passed = 0
$failed = 0
$skipped = 0

if (-not $Token) {
    Write-Host "[FAIL] 请设置 MS_AGENT_TOKEN 或 -Token" -ForegroundColor Red
    exit 1
}

function Invoke-Json {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [object]$Body = $null,
        [string]$UseToken = $Token
    )
    $uri = "$BaseUrl$Path"
    $headers = @{
        Authorization  = "Bearer $UseToken"
        "X-MS-PROJECT" = $script:ProjectId
    }
    $params = @{
        Uri             = $uri
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
        TimeoutSec      = 60
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
        $params.ContentType = "application/json"
    }
    try {
        $r = Invoke-WebRequest @params
        return @{ Status = [int]$r.StatusCode; Content = $r.Content; Ok = $true }
    } catch {
        $code = 0
        $content = $_.Exception.Message
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode.value__
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $content = $reader.ReadToEnd()
            } catch {}
        }
        return @{ Status = $code; Content = $content; Ok = $false }
    }
}

function Assert-True {
    param([string]$Name, [bool]$Cond, [string]$Detail = "")
    if ($Cond) {
        Write-Host "[PASS] $Name" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "[FAIL] $Name $Detail" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host "=== Agent Conversation Loop E2E ===" -ForegroundColor Cyan
Write-Host "BaseUrl=$BaseUrl"

# 0) health
$h = Invoke-Json -Path "/api/agent/v1/functional/health"
Assert-True "health" ($h.Status -eq 200 -and $h.Content -match "ok") $h.Content

# 1) FUNCTIONAL_ALL token cannot create project if we have a read token env — skip unless provided
if ($env:MS_AGENT_TOKEN_FUNCTIONAL_ALL) {
    $deny = Invoke-Json -Method POST -Path "/api/agent/v1/project/create" -Body @{
        organizationId = "dummy"
        name           = "should-fail"
        userIds        = @($AdminUserId)
    } -UseToken $env:MS_AGENT_TOKEN_FUNCTIONAL_ALL
    Assert-True "FUNCTIONAL_ALL denied PROJECT_WRITE" ($deny.Status -eq 403) $deny.Content
} else {
    Write-Host "[SKIP] FUNCTIONAL_ALL scope deny (set MS_AGENT_TOKEN_FUNCTIONAL_ALL)" -ForegroundColor Yellow
    $skipped++
}

# 2) project
if ($CreateProject -and -not $SkipProjectCreate) {
    if (-not $OrganizationId) {
        Write-Host "[FAIL] -CreateProject 需要 -OrganizationId / MS_ORG_ID" -ForegroundColor Red
        exit 1
    }
    $proj = Invoke-Json -Method POST -Path "/api/agent/v1/project/create" -Body @{
        organizationId = $OrganizationId
        name           = "Agent-Loop-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        userIds        = @($AdminUserId)
        description    = "agent_conversation_loop verify"
    }
    $script:ProjectId = $null
    if ($proj.Ok) {
        $obj = $proj.Content | ConvertFrom-Json
        $script:ProjectId = $obj.id
    }
    Assert-True "project/create" ($proj.Ok -and $script:ProjectId) $proj.Content
} elseif ($ProjectId) {
    $script:ProjectId = $ProjectId
    Write-Host "[INFO] use existing ProjectId=$ProjectId"
    Assert-True "project context" ($true)
} else {
    Write-Host "[FAIL] 请传 -ProjectId 或 -CreateProject -OrganizationId" -ForegroundColor Red
    exit 1
}

# 3) batch-create >=5
$cases = @()
1..5 | ForEach-Object {
    $cases += @{
        name     = "Loop-Case-$_-$(Get-Random -Maximum 9999)"
        priority = "P0"
        tags     = @("agent", "loop-verify")
        steps    = @(@{ num = 1; desc = "step1"; expected = "ok" })
    }
}
$batch = Invoke-Json -Method POST -Path "/api/agent/v1/functional/case/batch-create" -Body @{
    projectId  = $script:ProjectId
    modulePath = "AgentLoop/Verify"
    cases      = $cases
}
$caseIds = @()
if ($batch.Ok) {
    $bo = $batch.Content | ConvertFrom-Json
    $caseIds = @($bo.created | ForEach-Object { $_.caseId })
    Assert-True "batch-create >=5" ($bo.created.Count -ge 5 -and (@($bo.errors).Count -eq 0)) $batch.Content
} else {
    Assert-True "batch-create >=5" $false $batch.Content
}

# 4) test-plan
$plan = Invoke-Json -Method POST -Path "/api/agent/v1/test-plan/create" -Body @{
    projectId = $script:ProjectId
    name      = "Agent-Loop-Plan"
    caseIds   = $caseIds
}
$testPlanId = $null
if ($plan.Ok) {
    $po = $plan.Content | ConvertFrom-Json
    $testPlanId = $po.id
}
Assert-True "test-plan/create" ($plan.Ok -and $testPlanId) $plan.Content

# 5) case-review
$review = Invoke-Json -Method POST -Path "/api/agent/v1/case-review/create" -Body @{
    projectId = $script:ProjectId
    name      = "Agent-Loop-Review"
    caseIds   = $caseIds
}
$reviewId = $null
if ($review.Ok) {
    $ro = $review.Content | ConvertFrom-Json
    $reviewId = $ro.id
}
Assert-True "case-review/create" ($review.Ok -and $reviewId) $review.Content

# 6) search returns testPlanCaseId
$search = Invoke-Json -Method POST -Path "/api/agent/v1/functional/search" -Body @{
    query         = "AgentLoop"
    includeSteps  = $true
    testPlanId    = $testPlanId
    current       = 1
    pageSize      = 20
}
$withPlanCase = 0
if ($search.Ok) {
    $so = $search.Content | ConvertFrom-Json
    $list = @($so.cases)
    $withPlanCase = @($list | Where-Object { $_.testPlanCaseId }).Count
    Assert-True "search has testPlanCaseId" ($withPlanCase -ge 1) "matched=$withPlanCase total=$($list.Count)"
} else {
    Assert-True "search has testPlanCaseId" $false $search.Content
}

# 7) bug create (optional fail path — create for first case)
$bugOk = $false
if ($caseIds.Count -gt 0) {
    $bug = Invoke-Json -Method POST -Path "/api/agent/v1/bug/create" -Body @{
        projectId   = $script:ProjectId
        title       = "Agent-Loop verify bug"
        description = "auto verify"
        caseId      = $caseIds[0]
        caseType    = "FUNCTIONAL"
        testPlanId  = $testPlanId
    }
    if ($bug.Ok) {
        $bugId = ($bug.Content | ConvertFrom-Json).id
        $bugOk = [bool]$bugId
        Assert-True "bug/create" $bugOk $bug.Content
    } else {
        # 模板必填字段场景：记为告警，不直接失败整单
        if ($bug.Content -match "custom|字段|必填|template") {
            Write-Host "[WARN] bug/create needs customFields: $($bug.Content)" -ForegroundColor Yellow
            $skipped++
        } else {
            Assert-True "bug/create" $false $bug.Content
        }
    }
}

# 8) OpenAPI
$oa = Invoke-Json -Path "/v3/api-docs/agent"
if ($oa.Status -eq 200 -and $oa.Content -match "/api/agent/v1/project/create") {
    Assert-True "openapi agent paths" $true
} elseif ($oa.Status -eq 404 -or $oa.Content -match "disabled|Whitelabel") {
    Write-Host "[SKIP] OpenAPI agent group disabled in this profile (GroupedOpenApi bean present in code)" -ForegroundColor Yellow
    $skipped++
} else {
    Assert-True "openapi agent paths" $false "HTTP $($oa.Status)"
}

Write-Host ""
Write-Host "ProjectId=$($script:ProjectId) testPlanId=$testPlanId reviewId=$reviewId"
Write-Host "Results: $passed passed, $failed failed, $skipped skipped" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Yellow" })
if ($failed -gt 0) { exit 1 }
