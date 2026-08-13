param(
  [Parameter(Mandatory = $true)][string]$OutputDirectory,
  [Parameter(Mandatory = $true)][string]$NodeRuntimeDirectory,
  [string]$SigningCertificateThumbprint
)
$ErrorActionPreference = 'Stop'
if ($SigningCertificateThumbprint) {
  throw 'This script creates an unsigned internal ZIP. Sign a production executable in the release pipeline instead.'
}
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
$runtime = (Resolve-Path $NodeRuntimeDirectory).Path
if (-not (Test-Path (Join-Path $runtime 'node.exe'))) { throw 'NodeRuntimeDirectory must contain node.exe.' }
$runtimeArchitecture = & (Join-Path $runtime 'node.exe') -p "process.arch"
if ($runtimeArchitecture -ne 'x64') { throw "NodeRuntimeDirectory must contain an x64 runtime, but found $runtimeArchitecture." }
New-Item -ItemType Directory -Force -Path $output | Out-Null
$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npmCommand) { throw 'npm.cmd is required to restore Agent Bridge dependencies.' }
Push-Location $root
try {
  & $npmCommand.Source ci --omit=dev --ignore-scripts --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
$archive = Join-Path $output 'metersphere-agent-windows-x64.zip'
$staging = Join-Path $output 'metersphere-agent-package-staging'
if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $staging 'scripts\windows') | Out-Null
Copy-Item -Recurse -Force (Join-Path $root 'src') $staging
Copy-Item -Recurse -Force (Join-Path $root 'node_modules') $staging
Copy-Item -Recurse -Force $runtime (Join-Path $staging 'runtime')
Copy-Item -Force (Join-Path $root 'scripts\windows\install.ps1') (Join-Path $staging 'scripts\windows')
Copy-Item -Force (Join-Path $root 'scripts\windows\uninstall.ps1') (Join-Path $staging 'scripts\windows')
Copy-Item -Force (Join-Path $root 'Install-MeterSphere-Agent.cmd') $staging
Copy-Item -Force (Join-Path $root 'config.example.json') $staging
Copy-Item -Force (Join-Path $root 'package.json') $staging
Copy-Item -Force (Join-Path $root 'README.md') $staging
Compress-Archive -Force -Path (Join-Path $staging '*') -DestinationPath $archive
Remove-Item -LiteralPath $staging -Recurse -Force
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
$manifest = @{ file = (Split-Path $archive -Leaf); sha256 = $hash; version = '0.1.0'; signed = $false; distribution = 'internal' }
$manifest | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $output 'metersphere-agent-windows-x64.json')
"$hash  $(Split-Path $archive -Leaf)" | Set-Content -Encoding ascii (Join-Path $output 'metersphere-agent-windows-x64.zip.sha256')
Write-Host "Created $archive"
Write-Host "SHA-256: $hash"
