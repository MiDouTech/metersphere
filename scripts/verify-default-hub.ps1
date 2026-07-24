# MeterSphere 默认项目枢纽 - API 冒烟脚本（需已部署含本功能的后端）
# 用法示例:
#   $env:MS_BASE='https://msp.ebcone.net'
#   $env:MS_TOKEN='msat_xxx'
#   $env:MS_ORG='100001'
#   .\scripts\verify-default-hub.ps1

param(
    [string]$BaseUrl = $env:MS_BASE,
    [string]$Token = $env:MS_TOKEN,
    [string]$OrganizationId = $(if ($env:MS_ORG) { $env:MS_ORG } else { '100001' }),
    [string]$BizProjectId = $env:MS_BIZ_PROJECT
)

$ErrorActionPreference = 'Stop'
if (-not $BaseUrl -or -not $Token) {
    Write-Error '请设置 -BaseUrl/-Token 或环境变量 MS_BASE / MS_TOKEN'
}

$headers = @{
    'Accept'        = 'application/json'
    'Content-Type'  = 'application/json'
    'X-AUTH-TOKEN'  = $Token
}

function Invoke-Ms($Method, $Path, $Body = $null) {
    $uri = "$BaseUrl$Path"
    $params = @{ Uri = $uri; Method = $Method; Headers = $headers }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    $resp = Invoke-RestMethod @params
    if ($resp.code -and $resp.code -ne 100200 -and $resp.code -ne 200) {
        throw "API 失败 $Path code=$($resp.code) message=$($resp.message)"
    }
    if ($null -ne $resp.data) { return $resp.data }
    return $resp
}

Write-Host '== 1. 获取默认项目 ID =='
$hubId = Invoke-Ms GET '/default-hub/default-project-id'
Write-Host "hubProjectId=$hubId"
if (-not $hubId) { throw '默认项目未配置' }

Write-Host '== 2. 手动同步（可空 scope）=='
$syncJob = Invoke-Ms POST '/default-hub/sync' @{ projectId = $BizProjectId }
Write-Host "syncJob=$($syncJob.jobId) status=$($syncJob.status)"

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    $job = Invoke-Ms GET "/default-hub/sync/$($syncJob.jobId)"
    Write-Host "  progress=$($job.progress) status=$($job.status)"
    if ($job.status -eq 'SUCCESS' -or $job.status -eq 'FAILED') { break }
} while ((Get-Date) -lt $deadline)

if ($job.status -ne 'SUCCESS') {
    Write-Warning "同步未成功: $($job.errorMessage)"
} else {
    Write-Host '同步成功'
}

if ($BizProjectId) {
    Write-Host '== 3. 从默认项目导入用例(ALL/SKIP，可能 0 条则跳过) =='
    try {
        $importJob = Invoke-Ms POST '/functional/case/import/from-default-project' @{
            targetProjectId   = $BizProjectId
            selectMode        = 'ALL'
            conflictStrategy  = 'SKIP'
            ids               = @()
        }
        Write-Host "importCaseJob=$($importJob.jobId)"
    } catch {
        Write-Warning "用例导入启动失败（可因无用例）: $_"
    }
}

Write-Host '== 完成（请人工核对 T2-T16 清单 docs/task/default_project_cross_import/task011）=='
