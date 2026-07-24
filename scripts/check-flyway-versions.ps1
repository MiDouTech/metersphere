# Flyway version uniqueness check for MeterSphere
# Usage: powershell -File scripts/check-flyway-versions.ps1
# Exit 1 when duplicate V* version numbers exist across ddl/dml.

$ErrorActionPreference = "Stop"
$root = Join-Path $PSScriptRoot "..\backend\framework\domain\src\main\resources\migration"
$root = [System.IO.Path]::GetFullPath($root)

if (-not (Test-Path $root)) {
  Write-Host "Migration root not found: $root" -ForegroundColor Red
  exit 2
}

$items = Get-ChildItem -Recurse -File $root -Filter "V*.sql" | ForEach-Object {
  if ($_.Name -match '^(V[0-9]+(?:\.[0-9]+)*(?:_[0-9]+)?)__') {
    [PSCustomObject]@{
      Version = $Matches[1]
      File    = $_.FullName.Substring($root.Length).TrimStart('\', '/')
    }
  }
}

if (-not $items) {
  Write-Host "No migration files found under $root" -ForegroundColor Yellow
  exit 0
}

$dupes = $items | Group-Object Version | Where-Object { $_.Count -gt 1 }
Write-Host "Scanned $($items.Count) migration file(s) under migration/"
if ($dupes) {
  Write-Host "DUPLICATE Flyway versions detected (ddl/dml share one version space):" -ForegroundColor Red
  foreach ($d in $dupes) {
    Write-Host ("  {0}  x{1}" -f $d.Name, $d.Count) -ForegroundColor Red
    $d.Group | ForEach-Object { Write-Host ("    - {0}" -f $_.File) }
  }
  Write-Host "Fix: rename one script to next free V3.x.y_N (check BOTH ddl and dml)." -ForegroundColor Yellow
  exit 1
}

$max372 = $items |
  Where-Object { $_.Version -match '^V3\.7\.2_(\d+)$' } |
  ForEach-Object { [int]($_.Version -replace '^V3\.7\.2_', '') } |
  Measure-Object -Maximum

Write-Host "OK: all Flyway version numbers are unique." -ForegroundColor Green
if ($max372.Maximum) {
  Write-Host ("Next free 3.7.2 slot suggestion: V3.7.2_{0}" -f ($max372.Maximum + 1))
}
exit 0
