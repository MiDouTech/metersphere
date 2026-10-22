param(
    [ValidateSet('Frontend','Backend','McpPackage','Image','Start','Smoke')][string]$Stage = 'Frontend',
    [string]$EnvironmentFile = '.codex-tmp/case-export.env',
    [string]$AlpineMirror = 'https://dl-cdn.alpinelinux.org/alpine'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location $repo
function Run-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit $LASTEXITCODE" }
}
switch ($Stage) {
    'Frontend' {
        $env:NODE_OPTIONS = '--max-old-space-size=6144'
        Run-Checked 'pnpm.cmd' @('-C','frontend','run','build')
        Run-Checked 'pnpm.cmd' @('-C','frontend','exec','eslint',
            'src/api/modules/case-management/featureCase.ts',
            'src/views/case-management/caseManagementFeature/components/caseTable.vue')
        Run-Checked 'npm.cmd' @('--prefix','metersphere-mcp','test')
        Run-Checked 'node' @('frontend/scripts/test-case-export-notifications.mjs')
    }
    'Backend' {
        # New export tests create/drop a uniquely named database on the dedicated loopback test MySQL.
        Run-Checked '.\mvnw.cmd' @('-pl','backend/app','-am','package',
            '-Dtest=FunctionalCaseExport*Tests,CaseExportNotification*Tests,CaseExportTicketFilterTests,ExportWebSocketHandlerTests,Agent*Tests',
            '-Dquality.mysql=true','-Dquality.redis=true','-Dsurefire.failIfNoSpecifiedTests=false')
    }
    'McpPackage' {
        New-Item -ItemType Directory -Force -Path '.codex-tmp' | Out-Null
        Run-Checked 'npm.cmd' @('--prefix','metersphere-mcp','test')
        Run-Checked 'npm.cmd' @('pack','./metersphere-mcp','--pack-destination','.codex-tmp')
        $package = Get-Content -LiteralPath 'metersphere-mcp/package.json' -Raw | ConvertFrom-Json
        $tarball = './.codex-tmp/' + $package.name.Replace('@','').Replace('/','-') + '-' + $package.version + '.tgz'
        Run-Checked 'npm.cmd' @('install','--prefix','.codex-tmp/case-export-installed','--no-audit','--no-fund',$tarball)
    }
    'Image' {
        $jar = @(Get-ChildItem -LiteralPath 'backend/app/target' -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '(tests|sources|javadoc)' })
        if ($jar.Count -ne 1) { throw 'Expected one packaged application JAR.' }
        $hash = (Get-FileHash -LiteralPath $jar[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $context = Join-Path $repo "backend/app/target/case-export-image-$($hash.Substring(0,16))"
        New-Item -ItemType Directory -Force -Path "$context/app", "$context/wecom-bot-bridge/src", "$context/deploy" | Out-Null
        Push-Location "$context/app"
        try { Run-Checked 'jar' @('xf', $jar[0].FullName) } finally { Pop-Location }
        if ((Get-FileHash 'frontend/dist/index.html').Hash -ne (Get-FileHash "$context/app/BOOT-INF/classes/static/index.html").Hash) {
            throw 'JAR frontend is stale; run Frontend and Backend in order.'
        }
        Copy-Item -LiteralPath 'Dockerfile.backend' -Destination "$context/Dockerfile"
        Copy-Item -LiteralPath 'wecom-bot-bridge/package.json','wecom-bot-bridge/package-lock.json' -Destination "$context/wecom-bot-bridge"
        Copy-Item -Path 'wecom-bot-bridge/src/*' -Destination "$context/wecom-bot-bridge/src" -Recurse -Force
        Copy-Item -LiteralPath 'deploy/run-with-wecom-bridge.sh' -Destination "$context/deploy"
        Run-Checked 'docker' @('build','--build-arg','DEPENDENCY=app','--build-arg',"ALPINE_MIRROR=$AlpineMirror",'--build-arg',"MS_VERSION=case-export-$($hash.Substring(0,16))",'-t','msp-quality:case-export',$context)
        Write-Output "Application SHA256: $hash"
    }
    'Start' {
        if (!(Test-Path -LiteralPath $EnvironmentFile)) { throw 'Supply a private environment file with QUALITY_DB_PASSWORD, QUALITY_STORAGE_PASSWORD and QUALITY_FACTS_KEY.' }
        Run-Checked 'docker' @('compose','--env-file',$EnvironmentFile,'-p','msp-export-20261021','-f',
            'deploy/case-export-verify.compose.yml','up','-d','--wait')
        $health = Invoke-RestMethod 'http://127.0.0.1:17075/actuator/health/readiness'
        if ($health.status -ne 'UP') { throw 'Export acceptance backend is not ready.' }
        Get-Content -LiteralPath 'deploy/quality-verify/case-export-fixture.sql' -Raw |
            docker compose --env-file $EnvironmentFile -p msp-export-20261021 -f deploy/case-export-verify.compose.yml exec -T mysql sh -c 'export MYSQL_PWD=$MYSQL_ROOT_PASSWORD; exec mysql -uroot metersphere_quality'
        if ($LASTEXITCODE -ne 0) { throw 'Isolated export fixture failed.' }
        Write-Output 'Export acceptance readiness: UP'
    }
    'Smoke' {
        # Run node scripts/case-export-browser-proxy.mjs in another terminal first.
        $env:PW_BASE_URL = 'http://127.0.0.1:15175'
        $env:CASE_EXPORT_E2E = 'true'
        Run-Checked 'pnpm.cmd' @('-C','frontend','exec','playwright','test','e2e/case-export-recovery.spec.ts','--workers=1')
    }
}
