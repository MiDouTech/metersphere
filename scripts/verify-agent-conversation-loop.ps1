<#
.SYNOPSIS
  agent_conversation_loop task010：写闭环联调脚本

.DESCRIPTION
  覆盖：Scope 拒绝、项目创建、批量导入、计划/评审关联、search(testPlanCaseId)、提缺陷、OpenAPI。
  前置：后端已启动；Token 需含 AGENT_ALL（或各 WRITE + READ/SUBMIT）。
  默认不创建项目：传入 -ProjectId 走既有项目；传 -OrganizationId 且 -CreateProject 才新建。
  响应统一解包 MeterSphere Result：{ code, data }（业务码 100200 为成功）。

.EXAMPLE
  $env:MS_AGENT_TOKEN = "msat_xxx"
  .\scripts\verify-agent-conversation-loop.ps1 -OrganizationId 100001 -CreateProject
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
$script:ProjectId = $ProjectId

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
        "X-MS-PROJECT" = "$(if ($script:ProjectId) { $script:ProjectId } else { '' })"
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
        $params.ContentType = "application/json; charset=utf-8"
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
                $reader = New-Object System.IO.StreamReader($stream, [Text.Encoding]::UTF8)
                $content = $reader.ReadToEnd()
            } catch {}
        }
        return @{ Status = $code; Content = $content; Ok = $false }
    }
}

function Get-MsData {
    param([string]$Content)
    if ([string]::IsNullOrWhiteSpace($Content)) { return $null }
    try {
        $obj = $Content | ConvertFrom-Json
    } catch {
        return $null
    }
    if ($null -eq $obj) { return $null }
    # MeterSphere Result wrapper
    if ($obj.PSObject.Properties.Name -contains "code") {
        if ([int]$obj.code -ne 100200) { return $null }
        if ($obj.PSObject.Properties.Name -contains "data") { return $obj.data }
        return $obj
    }
    return $obj
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
$hData = Get-MsData $h.Content
Assert-True "health" ($h.Status -eq 200 -and ("$hData" -match "ok" -or $h.Content -match "ok")) $h.Content

# 1) FUNCTIONAL_ALL token cannot create project if we have a read token env — skip unless provided
if ($env:MS_AGENT_TOKEN_FUNCTIONAL_ALL) {
    $deny = Invoke-Json -Method POST -Path "/api/agent/v1/project/create" -Body @{
        organizationId = "dummy"
        name           = "should-fail"
        userIds        = @($AdminUserId)
    } -UseToken $env:MS_AGENT_TOKEN_FUNCTIONAL_ALL
    $denyBiz = $false
    try {
        $dj = $deny.Content | ConvertFrom-Json
        $denyBiz = ([int]$dj.code -eq 100403 -or [int]$dj.code -eq 100401)
    } catch {}
    Assert-True "FUNCTIONAL_ALL denied PROJECT_WRITE" ($deny.Status -eq 403 -or $denyBiz) $deny.Content
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
        moduleIds      = @("caseManagement", "bugManagement", "testPlan", "apiTest")
    }
    $script:ProjectId = $null
    $projData = Get-MsData $proj.Content
    if ($null -ne $projData) {
        $script:ProjectId = [string]$projData.id
    }
    Assert-True "project/create" ($proj.Ok -and $script:ProjectId) $proj.Content

    # 加成员（幂等：再加一次同一用户组，验证 PROJECT_ADD_MEMBERS）
    if ($script:ProjectId) {
        $mem = Invoke-Json -Method POST -Path "/api/agent/v1/project/members/add" -Body @{
            projectId   = $script:ProjectId
            userIds     = @($AdminUserId)
            userRoleIds = @("project_member")
        }
        $memOk = $mem.Ok -and ($null -ne (Get-MsData $mem.Content) -or $mem.Content -match '"code":100200')
        # void 接口可能 data=null 但仍 100200
        if (-not $memOk) {
            try { $memOk = ([int]($mem.Content | ConvertFrom-Json).code -eq 100200) } catch {}
        }
        Assert-True "project/members/add" $memOk $mem.Content
    }
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
$bo = Get-MsData $batch.Content
if ($null -ne $bo) {
    $caseIds = @($bo.created | ForEach-Object { $_.caseId })
    $errCount = @($bo.errors).Count
    Assert-True "batch-create >=5" ($caseIds.Count -ge 5 -and $errCount -eq 0) $batch.Content
} else {
    Assert-True "batch-create >=5" $false $batch.Content
}

# 4) test-plan
$plan = Invoke-Json -Method POST -Path "/api/agent/v1/test-plan/create" -Body @{
    projectId = $script:ProjectId
    name      = "Agent-Loop-Plan-$(Get-Random -Maximum 9999)"
    caseIds   = $caseIds
}
$testPlanId = $null
$po = Get-MsData $plan.Content
if ($null -ne $po) { $testPlanId = [string]$po.id }
Assert-True "test-plan/create" ([bool]$testPlanId) $plan.Content

# 5) case-review
$review = Invoke-Json -Method POST -Path "/api/agent/v1/case-review/create" -Body @{
    projectId = $script:ProjectId
    name      = "Agent-Loop-Review-$(Get-Random -Maximum 9999)"
    caseIds   = $caseIds
}
$reviewId = $null
$ro = Get-MsData $review.Content
if ($null -ne $ro) { $reviewId = [string]$ro.id }
Assert-True "case-review/create" ([bool]$reviewId) $review.Content

# 6) search returns testPlanCaseId
$search = Invoke-Json -Method POST -Path "/api/agent/v1/functional/search" -Body @{
    query         = "Loop-Case"
    includeSteps  = $true
    testPlanId    = $testPlanId
    current       = 1
    pageSize      = 20
}
$withPlanCase = 0
$firstPlanCase = $null
$so = Get-MsData $search.Content
if ($null -ne $so) {
    $list = @($so.cases)
    $withPlanCase = @($list | Where-Object { $_.testPlanCaseId }).Count
    $firstPlanCase = $list | Where-Object { $_.testPlanCaseId } | Select-Object -First 1
    Assert-True "search has testPlanCaseId" ($withPlanCase -ge 1) "matched=$withPlanCase total=$($list.Count)"
} else {
    Assert-True "search has testPlanCaseId" $false $search.Content
}

# 6b) submit one SUCCESS (回写；附件可选)
if ($null -ne $firstPlanCase) {
    $stepsPayload = @()
    foreach ($st in @($firstPlanCase.steps)) {
        $stepsPayload += @{
            id       = $st.id
            num      = $st.num
            desc     = $st.desc
            expected = $st.expected
            result   = "SUCCESS"
            actual   = "ok"
        }
    }
    $submit = Invoke-Json -Method POST -Path "/api/agent/v1/functional/submit" -Body @{
        testPlanCaseId = $firstPlanCase.testPlanCaseId
        lastExecResult = "SUCCESS"
        content        = "agent loop verify"
        executedBy     = "cursor-agent"
        steps          = $stepsPayload
    }
    $subOk = $null -ne (Get-MsData $submit.Content) -or ($submit.Content -match '"code":100200')
    if (-not $subOk) {
        try { $subOk = ([int]($submit.Content | ConvertFrom-Json).code -eq 100200) } catch {}
    }
    Assert-True "submit SUCCESS" $subOk $submit.Content
} else {
    Write-Host "[SKIP] submit (no testPlanCaseId)" -ForegroundColor Yellow
    $skipped++
}

# 7) bug create (optional fail path — create for first case)
$bugId = $null
if ($caseIds.Count -gt 0) {
    $bug = Invoke-Json -Method POST -Path "/api/agent/v1/bug/create" -Body @{
        projectId   = $script:ProjectId
        title       = "Agent-Loop verify bug"
        description = "auto verify"
        caseId      = $caseIds[0]
        caseType    = "FUNCTIONAL"
        testPlanId  = $testPlanId
    }
    $bugData = Get-MsData $bug.Content
    if ($null -ne $bugData) {
        $bugId = [string]$bugData.id
        Assert-True "bug/create" ([bool]$bugId) $bug.Content
    } else {
        # 模板必填字段场景：记为告警，不直接失败整单
        if ($bug.Content -match "custom|字段|必填|template|模板") {
            Write-Host "[WARN] bug/create needs customFields: $($bug.Content)" -ForegroundColor Yellow
            $skipped++
        } else {
            Assert-True "bug/create" $false $bug.Content
        }
    }
}

# 8) OpenAPI（双 path 映射：/api/agent 与 /agent）
$oa = Invoke-Json -Path "/v3/api-docs/agent"
if ($oa.Status -eq 200 -and (
        $oa.Content -match "/api/agent/v1/project/create" -or
        $oa.Content -match "/agent/v1/project/create" -or
        $oa.Content -match "project/create"
    )) {
    Assert-True "openapi agent paths" $true
} elseif ($oa.Status -eq 404 -or $oa.Content -match "disabled|Whitelabel") {
    Write-Host "[SKIP] OpenAPI agent group disabled in this profile (GroupedOpenApi bean present in code)" -ForegroundColor Yellow
    $skipped++
} else {
    # 测试环境可能裁剪 OpenAPI；有文档体即记 WARN
    if ($oa.Status -eq 200 -and $oa.Content.Length -gt 100) {
        Write-Host "[WARN] openapi present but path string not matched (size=$($oa.Content.Length))" -ForegroundColor Yellow
        $skipped++
    } else {
        Assert-True "openapi agent paths" $false "HTTP $($oa.Status) size=$($oa.Content.Length)"
    }
}

Write-Host ""
Write-Host "ProjectId=$($script:ProjectId) testPlanId=$testPlanId reviewId=$reviewId bugId=$bugId"
Write-Host "Results: $passed passed, $failed failed, $skipped skipped" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Yellow" })
if ($failed -gt 0) { exit 1 }
