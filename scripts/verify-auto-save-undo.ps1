# MeterSphere Auto-Save / Undo smoke checklist helper
# Usage:
#   pwsh scripts/verify-auto-save-undo.ps1
#   pwsh scripts/verify-auto-save-undo.ps1 -BaseUrl https://ms.example.com -Token <jwt>
#
# Without -Token: prints manual checklist only (no API calls).

param(
  [string]$BaseUrl = "",
  [string]$Token = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=== Auto-Save / Undo verification ===" -ForegroundColor Cyan
Write-Host "Checklist doc: docs/task/auto_save_undo/task009-P0-端到端验收与回归.md"
Write-Host ""

$manual = @(
  "T1  Debounce/blur autosave then refresh",
  "T2  Failed save blocks leave",
  "T3  Explicit save + Ctrl+S",
  "T4  Undo max 2 steps",
  "T5  Redo",
  "T6  Undo after refresh (cross-session)",
  "T7  Other user read-only lock message",
  "T8  15min idle lock release",
  "T9  No permission => no autosave",
  "T10 Attachments not rolled back by Undo",
  "T11 Batch/import has no mid-state autosave"
)

Write-Host "Manual scenarios (run on CASE / BUG / PLAN_DOCUMENT):" -ForegroundColor Yellow
foreach ($item in $manual) {
  Write-Host "  [ ] $item"
}

if (-not $BaseUrl -or -not $Token) {
  Write-Host ""
  Write-Host "Skip API probe (provide -BaseUrl and -Token to check flags)." -ForegroundColor DarkGray
  exit 0
}

$headers = @{
  Authorization = "Bearer $Token"
  "Content-Type" = "application/json"
}

function Invoke-MsGet([string]$Path) {
  $url = ($BaseUrl.TrimEnd("/") + $Path)
  return Invoke-RestMethod -Method Get -Uri $url -Headers $headers
}

Write-Host ""
Write-Host "API flag probe:" -ForegroundColor Cyan
try {
  $autosave = Invoke-MsGet "/resource-edit/autosave-enabled"
  $writePath = Invoke-MsGet "/resource-edit/writepath-snapshot-enabled"
  Write-Host ("  autosave-enabled              = {0}" -f $autosave)
  Write-Host ("  writepath-snapshot-enabled    = {0}" -f $writePath)
  Write-Host "OK: resource-edit endpoints reachable." -ForegroundColor Green
} catch {
  Write-Host ("FAIL: {0}" -f $_.Exception.Message) -ForegroundColor Red
  exit 1
}
