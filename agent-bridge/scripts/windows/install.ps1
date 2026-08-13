param(
  [string]$InstallRoot = "$env:LOCALAPPDATA\MeterSphere\Agent",
  [string]$PackageRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\.."))
)
$ErrorActionPreference = 'Stop'
$bundledNode = Join-Path $PackageRoot 'runtime\node.exe'
if (-not (Test-Path $bundledNode)) { throw 'The MeterSphere Agent package is missing its bundled Node.js runtime.' }
if (-not (Test-Path (Join-Path $PackageRoot 'src\main.mjs'))) { throw 'The MeterSphere Agent package is incomplete.' }
New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
Copy-Item -Recurse -Force (Join-Path $PackageRoot 'src') $InstallRoot
Copy-Item -Recurse -Force (Join-Path $PackageRoot 'node_modules') $InstallRoot
Copy-Item -Force (Join-Path $PackageRoot 'package.json') $InstallRoot
Copy-Item -Recurse -Force (Join-Path $PackageRoot 'runtime') $InstallRoot
New-Item -ItemType Directory -Force -Path (Join-Path $InstallRoot 'scripts\windows') | Out-Null
Copy-Item -Force (Join-Path $PackageRoot 'scripts\windows\uninstall.ps1') (Join-Path $InstallRoot 'scripts\windows')
if (-not (Test-Path (Join-Path $InstallRoot 'config.json'))) {
  Copy-Item -Force (Join-Path $PackageRoot 'config.example.json') (Join-Path $InstallRoot 'config.json')
}
$installedNode = Join-Path $InstallRoot 'runtime\node.exe'
$command = '"{0}" "{1}" "%1"' -f $installedNode, (Join-Path $InstallRoot 'src\protocol-handler.mjs')
$protocolKey = 'HKCU:\Software\Classes\metersphere-agent'
New-Item -Force $protocolKey | Out-Null
Set-ItemProperty $protocolKey -Name '(default)' -Value 'URL:MeterSphere Agent Protocol'
Set-ItemProperty $protocolKey -Name 'URL Protocol' -Value ''
New-Item -Force "$protocolKey\shell\open\command" | Out-Null
Set-ItemProperty "$protocolKey\shell\open\command" -Name '(default)' -Value $command
$runCommand = '"{0}" "{1}"' -f $installedNode, (Join-Path $InstallRoot 'src\main.mjs')
New-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name 'MeterSphereAgent' -Value $runCommand -PropertyType String -Force | Out-Null
$configPath = Join-Path $InstallRoot 'config.json'
$paired = $false
try {
  $config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
  $paired = -not [string]::IsNullOrWhiteSpace([string]$config.deviceId)
} catch {
  Write-Warning 'The existing configuration could not be read. Pair the Agent again from MeterSphere.'
}
if ($paired) {
  Start-Process -FilePath $installedNode -ArgumentList (Join-Path $InstallRoot 'src\main.mjs') -WorkingDirectory $InstallRoot -WindowStyle Hidden
  Write-Host 'The previously paired MeterSphere Agent was started.'
}
Write-Host 'MeterSphere Agent was installed for the current user.'
Write-Host 'Return to MeterSphere and click "Installed - detect again" to finish pairing.'
