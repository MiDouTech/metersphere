param(
  [string]$InstallRoot = "$env:LOCALAPPDATA\MeterSphere\Agent",
  [switch]$RemoveApplicationData
)
$ErrorActionPreference = 'Stop'
Remove-Item 'HKCU:\Software\Classes\metersphere-agent' -Recurse -Force -ErrorAction SilentlyContinue
Remove-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name 'MeterSphereAgent' -ErrorAction SilentlyContinue
Write-Host "Protocol and auto-start registration removed. Application data remains at $InstallRoot for recovery."
if ($RemoveApplicationData -and (Test-Path $InstallRoot)) {
  $resolved = (Resolve-Path $InstallRoot).Path
  $expected = [IO.Path]::GetFullPath("$env:LOCALAPPDATA\MeterSphere\Agent")
  if ($resolved -ne $expected) { throw 'Refusing to remove an unexpected application directory.' }
  Remove-Item -LiteralPath $resolved -Recurse -Force
  Write-Host 'MeterSphere Agent application data removed.'
}
