param([ValidateSet('Package', 'Image', 'Start', 'Health')][string]$Stage = 'Health',
    [string]$AlpineMirror = 'https://dl-cdn.alpinelinux.org/alpine')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed: $LASTEXITCODE" }
}
$compose = @('compose', '--env-file', '.codex-tmp/sql-stability.env', '-p', 'msp-sql-stability20261023', '-f', 'deploy/sql-stability-verify.compose.yml')
switch ($Stage) {
    'Package' {
        Checked '.\mvnw.cmd' @('-pl', 'backend/app', '-am', 'package',
            '-Dtest=DatabaseWorkloadFilterTests,RequestTraceFilterTests,SqlStabilityMySqlTests,AgentBatchReadTests,AgentLibrarySearchMySqlTests,AgentFunctionalCaseSearchServiceTests,AgentQualityFinalizationMySqlTests,AgentRecoveryScanMySqlTests,AgentLegacyImportMySqlTests,AgentRecoveryScanHealthTests',
            '-Dsurefire.failIfNoSpecifiedTests=false', '-Dquality.mysql=true')
    }
    'Image' {
        $jar = Get-Item -LiteralPath 'backend/app/target/app-3.x.jar'
        $hash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $context = Join-Path $PWD "backend/app/target/sql-stability-image-$($hash.Substring(0,16))"
        New-Item -ItemType Directory -Force -Path "$context/app", "$context/wecom-bot-bridge/src", "$context/deploy" | Out-Null
        Push-Location "$context/app"
        try { Checked 'jar' @('xf', $jar.FullName) } finally { Pop-Location }
        if ((Get-FileHash 'frontend/dist/index.html').Hash -ne (Get-FileHash "$context/app/BOOT-INF/classes/static/index.html").Hash) {
            throw 'Packaged frontend is stale; rebuild frontend and package again.'
        }
        Copy-Item -LiteralPath 'Dockerfile.backend' -Destination "$context/Dockerfile"
        Copy-Item -LiteralPath 'wecom-bot-bridge/package.json', 'wecom-bot-bridge/package-lock.json' -Destination "$context/wecom-bot-bridge"
        Copy-Item -Path 'wecom-bot-bridge/src/*' -Destination "$context/wecom-bot-bridge/src" -Recurse -Force
        Copy-Item -LiteralPath 'deploy/run-with-wecom-bridge.sh' -Destination "$context/deploy"
        Checked 'docker' @('build', '--build-arg', 'DEPENDENCY=app', '--build-arg', "ALPINE_MIRROR=$AlpineMirror", '--build-arg', "MS_VERSION=sql-stability-$($hash.Substring(0,16))", '-t', 'msp-sql-stability:20261023', $context)
        Write-Output "Application SHA256: $hash"
    }
    'Start' {
        Checked 'docker' ($compose + @('config', '--quiet'))
        Checked 'docker' ($compose + @('up', '-d'))
    }
    'Health' {
        Checked 'docker' ($compose + @('ps'))
        $health=Invoke-RestMethod 'http://127.0.0.1:17091/actuator/health/readiness'
        if($health.status -ne 'UP') { throw 'Application is not ready' }
        $health | ConvertTo-Json -Depth 5
    }
}
