param(
    [ValidateSet('Validate', 'Image', 'Start', 'Smoke')]
    [string]$Stage = 'Validate'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location $repo
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed (exit $LASTEXITCODE)." }
}
switch ($Stage) {
    'Validate' {
        Invoke-Checked 'pnpm.cmd' @('-C', 'frontend', 'run', 'build')
        Invoke-Checked 'npm.cmd' @('--prefix', 'metersphere-mcp', 'test')
        New-Item -ItemType Directory -Force -Path '.codex-tmp/mcp-remediation-installed' | Out-Null
        Invoke-Checked 'npm.cmd' @('pack', './metersphere-mcp', '--pack-destination', '.codex-tmp')
        Invoke-Checked 'npm.cmd' @('install', '--prefix', '.codex-tmp/mcp-remediation-installed',
            '--no-audit', '--no-fund', './.codex-tmp/midoo-metersphere-mcp-0.7.0.tgz')
        # Integration tests use only the dedicated local MySQL on port 13326.
        Invoke-Checked '.\mvnw.cmd' @('-pl', 'backend/app', '-am', 'package',
            '-Dtest=Agent*Tests', '-Dquality.mysql=true', '-Dsurefire.failIfNoSpecifiedTests=false')
    }
    'Image' {
        $jar = @(Get-ChildItem -LiteralPath 'backend/app/target' -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '(tests|sources|javadoc)' })
        if ($jar.Count -ne 1) { throw 'Expected exactly one packaged application JAR; run Validate first.' }
        $hash = (Get-FileHash -LiteralPath $jar[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $context = Join-Path $repo "backend/app/target/remediation-image-$($hash.Substring(0,16))"
        New-Item -ItemType Directory -Force -Path "$context/app", "$context/wecom-bot-bridge/src", "$context/deploy" | Out-Null
        Push-Location "$context/app"
        try { Invoke-Checked 'jar' @('xf', $jar[0].FullName) } finally { Pop-Location }
        $sourceIndex = Get-FileHash -LiteralPath 'frontend/dist/index.html'
        $jarIndex = Get-FileHash -LiteralPath "$context/app/BOOT-INF/classes/static/index.html"
        if ($sourceIndex.Hash -ne $jarIndex.Hash) { throw 'JAR contains stale frontend resources; rerun Validate.' }
        Copy-Item -LiteralPath 'Dockerfile.backend' -Destination "$context/Dockerfile"
        Copy-Item -LiteralPath 'wecom-bot-bridge/package.json', 'wecom-bot-bridge/package-lock.json' -Destination "$context/wecom-bot-bridge"
        Copy-Item -Path 'wecom-bot-bridge/src/*' -Destination "$context/wecom-bot-bridge/src" -Recurse -Force
        Copy-Item -LiteralPath 'deploy/run-with-wecom-bridge.sh' -Destination "$context/deploy"
        Invoke-Checked 'docker' @('build', '--build-arg', 'DEPENDENCY=app', '--build-arg',
            "MS_VERSION=remediation-$($hash.Substring(0,16))", '-t', 'msp-quality:remediation', $context)
        Write-Output "Packaged application SHA256: $hash"
    }
    'Start' {
        # Secrets must be supplied explicitly or retained from this exact isolated container.
        if (!$env:QUALITY_DB_PASSWORD -or !$env:QUALITY_STORAGE_PASSWORD -or !$env:QUALITY_FACTS_KEY) {
            $existing = & docker inspect msp-quality-verify-app-1
            if ($LASTEXITCODE -ne 0) { throw 'Set QUALITY_DB_PASSWORD, QUALITY_STORAGE_PASSWORD and QUALITY_FACTS_KEY for the isolated environment.' }
            $settings = @{}
            foreach ($entry in ($existing | ConvertFrom-Json)[0].Config.Env) {
                $parts = $entry.Split('=', 2); $settings[$parts[0]] = $parts[1]
            }
            if (!$env:QUALITY_DB_PASSWORD) { $env:QUALITY_DB_PASSWORD = $settings['SPRING_DATASOURCE_PASSWORD'] }
            if (!$env:QUALITY_STORAGE_PASSWORD) { $env:QUALITY_STORAGE_PASSWORD = $settings['MINIO_SECRET_KEY'] }
            if (!$env:QUALITY_FACTS_KEY) { $env:QUALITY_FACTS_KEY = $settings['AGENT_EXECUTION_FACTS_KEY'] }
        }
        if (!$env:QUALITY_FACTS_KEY) { throw 'A persistent facts key is required; refusing to replace it.' }
        Invoke-Checked 'docker' @('tag', 'msp-quality:verify', 'msp-quality:pre-remediation')
        Invoke-Checked 'docker' @('tag', 'msp-quality:remediation', 'msp-quality:verify')
        Invoke-Checked 'docker' @('compose', '-p', 'msp-quality-verify', '-f',
            'deploy/quality-verify.compose.yml', 'up', '-d', '--no-deps', '--wait', 'app')
        & "$PSScriptRoot/verify-quality-environment.ps1"
        if ($LASTEXITCODE -ne 0) { throw 'Isolated health checks failed.' }
        $sql = "SELECT COUNT(*) FROM metersphere_version WHERE version='3.7.2.103' AND success=1 AND checksum IS NOT NULL;"
        $result = $sql | & docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N metersphere_quality'
        if ($LASTEXITCODE -ne 0 -or ($result | Out-String).Trim() -ne '1') { throw 'Evidence review migration 103 is missing.' }
    }
    'Smoke' {
        $env:PW_BASE_URL = 'http://127.0.0.1:15173'
        $env:MCP_CASE_LIBRARY_E2E = 'true'
        $env:QUALITY_E2E = 'true'
        $env:MCP_INSTALLED_E2E = 'true'
        # Start scripts/quality-browser-proxy.mjs separately; it serves the built dist and isolated API.
        Invoke-Checked 'pnpm.cmd' @('-C', 'frontend', 'exec', 'playwright', 'test',
            'e2e/remediation-navigation.spec.ts', 'e2e/personal-agent-execution.spec.ts',
            'e2e/quality-policy.spec.ts', '--workers=1')
    }
}
