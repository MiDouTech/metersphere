$ErrorActionPreference = "Stop"
$ArchiveRoot = Split-Path -Parent $PSScriptRoot
$ProjectRoot = Split-Path -Parent $ArchiveRoot
$ConfigFile = Join-Path $ArchiveRoot "nacos\metersphere.properties"
$NacosAddr = if ($env:NACOS_SERVER_ADDR) { $env:NACOS_SERVER_ADDR } else { "127.0.0.1:8848" }
$Namespace = if ($env:NACOS_NAMESPACE) { $env:NACOS_NAMESPACE } else { "dev" }
$Group = if ($env:NACOS_GROUP) { $env:NACOS_GROUP } else { "METERSPHERE" }
$Username = if ($env:NACOS_USERNAME) { $env:NACOS_USERNAME } else { "nacos" }
$Password = if ($env:NACOS_PASSWORD) { $env:NACOS_PASSWORD } else { "nacos" }
$DataId = "metersphere.properties"

function Wait-Nacos {
    param([string]$BaseUrl, [int]$MaxAttempts = 60)
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "$BaseUrl/nacos/v1/console/health/readiness" -TimeoutSec 3
            if ($resp -eq "OK") { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    throw "Nacos is not ready at $BaseUrl"
}

function Get-NamespaceId {
    param([string]$BaseUrl, [string]$Name)
    $auth = @{ username = $Username; password = $Password }
    $list = Invoke-RestMethod -Uri "$BaseUrl/nacos/v1/console/namespaces" -Method Get @auth
    $found = $list.data | Where-Object { $_.namespaceShowName -eq $Name -or $_.namespace -eq $Name }
    if ($found) { return $found.namespace }
    $createBody = @{
        customNamespaceId = $Name
        namespaceName = $Name
        namespaceDesc = "MeterSphere $Name"
    }
    Invoke-RestMethod -Uri "$BaseUrl/nacos/v1/console/namespaces" -Method Post -Body $createBody @auth | Out-Null
    return $Name
}

$baseUrl = "http://$NacosAddr"
Wait-Nacos -BaseUrl $baseUrl
$namespaceId = Get-NamespaceId -BaseUrl $baseUrl -Name $Namespace
$content = Get-Content -Path $ConfigFile -Raw -Encoding UTF8

$params = @{
    dataId = $DataId
    group = $Group
    tenant = $namespaceId
    type = "properties"
    content = $content
    username = $Username
    password = $Password
}

Invoke-RestMethod -Uri "$baseUrl/nacos/v1/cs/configs" -Method Post -Body $params | Out-Null
Write-Host "Published $DataId to Nacos (namespace=$Namespace, group=$Group)."
