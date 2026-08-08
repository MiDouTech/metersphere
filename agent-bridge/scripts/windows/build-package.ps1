param(
  [Parameter(Mandatory = $true)][string]$OutputDirectory,
  [Parameter(Mandatory = $true)][string]$NodeRuntimeDirectory,
  [string]$SigningCertificateThumbprint
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
$runtime = (Resolve-Path $NodeRuntimeDirectory).Path
if (-not (Test-Path (Join-Path $runtime 'node.exe'))) { throw 'NodeRuntimeDirectory must contain node.exe.' }
New-Item -ItemType Directory -Force -Path $output | Out-Null
Push-Location $root
try { npm ci --omit=dev } finally { Pop-Location }
$archive = Join-Path $output 'metersphere-agent-windows-x64.zip'
$staging = Join-Path $output 'metersphere-agent-package-staging'
if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $staging 'scripts\windows') | Out-Null
Copy-Item -Recurse -Force (Join-Path $root 'src') $staging
Copy-Item -Recurse -Force (Join-Path $root 'node_modules') $staging
Copy-Item -Recurse -Force $runtime (Join-Path $staging 'runtime')
Copy-Item -Force (Join-Path $root 'scripts\windows\install.ps1') (Join-Path $staging 'scripts\windows')
Copy-Item -Force (Join-Path $root 'scripts\windows\uninstall.ps1') (Join-Path $staging 'scripts\windows')
Copy-Item -Force (Join-Path $root 'config.example.json') $staging
Copy-Item -Force (Join-Path $root 'package.json') $staging
Compress-Archive -Force -Path (Join-Path $staging '*') -DestinationPath $archive
Remove-Item -LiteralPath $staging -Recurse -Force
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
$manifest = @{ file = (Split-Path $archive -Leaf); sha256 = $hash; version = '0.1.0'; signed = $false }
if ($SigningCertificateThumbprint) {
  $manifest.signed = $true
  $manifest.signingCertificateThumbprint = $SigningCertificateThumbprint
  Write-Warning 'The ZIP manifest records the release certificate, but executable signing must be performed by the release pipeline.'
}
$manifest | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $output 'metersphere-agent-windows-x64.json')
