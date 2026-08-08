param(
  [string]$InstallRoot = "$env:LOCALAPPDATA\MeterSphere\Agent",
  [string]$PackageRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\.."))
)
$ErrorActionPreference = 'Stop'
$bundledNode = Join-Path $PackageRoot 'runtime\node.exe'
if (-not (Test-Path $bundledNode)) { throw 'The signed MeterSphere Agent package is missing its bundled Node.js runtime.' }
New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
Copy-Item -Recurse -Force (Join-Path $PackageRoot 'src') $InstallRoot
Copy-Item -Recurse -Force (Join-Path $PackageRoot 'node_modules') $InstallRoot
Copy-Item -Force (Join-Path $PackageRoot 'package.json') $InstallRoot
Copy-Item -Recurse -Force (Join-Path $PackageRoot 'runtime') $InstallRoot
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
Write-Host 'MeterSphere Agent installed for the current user.'
