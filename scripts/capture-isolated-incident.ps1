param(
    [Parameter(Mandatory=$true)][string]$ComposeFile,
    [string]$ProjectName = 'msp-implementation20261022',
    [string]$OutputDirectory = 'outputs/implementation-20261022/incident'
)
$ErrorActionPreference = 'Stop'
if ($ProjectName -notmatch '^msp-(implementation[0-9]+|quality-verify|export-[0-9]+)$') {
    throw 'This collector only accepts explicitly isolated test project names.'
}
$root = (Resolve-Path '.').Path
$destination = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
if (-not $destination.StartsWith((Join-Path $root 'outputs') + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Evidence output must remain under this workspace outputs directory.'
}
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$manifest = @()
function Capture([string]$Name, [string[]]$Arguments) {
    $result = & docker @Arguments 2>&1
    $code = $LASTEXITCODE
    $result | Set-Content -LiteralPath (Join-Path $destination "$Name.txt") -Encoding UTF8
    $script:manifest += [pscustomobject]@{ check=$Name; exitCode=$code }
}
Capture 'containers' @('compose','-p',$ProjectName,'-f',$ComposeFile,'ps','--all')
$ids = @(& docker compose -p $ProjectName -f $ComposeFile ps -q)
if ($LASTEXITCODE -ne 0 -or -not $ids.Count) { throw 'No isolated containers found.' }
Capture 'resources' (@('stats','--no-stream','--format','{{json .}}') + $ids)
Capture 'threads' @('compose','-p',$ProjectName,'-f',$ComposeFile,'exec','-T','app','sh','-c','command -v jcmd >/dev/null && jcmd $(pgrep -o java) Thread.print')
Capture 'mysql-waits' @('compose','-p',$ProjectName,'-f',$ComposeFile,'exec','-T','mysql','sh','-c','MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -e "SHOW GLOBAL STATUS WHERE Variable_name IN (''Threads_running'',''Threads_connected'',''Aborted_connects''); SELECT * FROM performance_schema.data_lock_waits LIMIT 50;"')
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'manifest.json') -Encoding UTF8
Write-Output "Incident snapshots saved under $destination; inspect each exitCode for unavailable diagnostics."
