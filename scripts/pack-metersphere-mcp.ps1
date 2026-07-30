# Pack metersphere-mcp into agent-integration classpath resources.
# Run from repo root after `cd metersphere-mcp && npm run build`.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $Root "metersphere-mcp"))) {
    $Root = (Get-Location).Path
}

$McpDir = Join-Path $Root "metersphere-mcp"
$DistDir = Join-Path $McpDir "dist"
$PkgJson = Join-Path $McpDir "package.json"

if (-not (Test-Path $DistDir)) {
    throw "Missing $DistDir — run: cd metersphere-mcp; npm run build"
}

$Version = (Get-Content $PkgJson -Raw | ConvertFrom-Json).version
$OutDir = Join-Path $Root "backend\services\agent-integration\src\main\resources\mcp"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$Stage = Join-Path $env:TEMP "metersphere-mcp-pack-$Version"
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
$StageRoot = Join-Path $Stage "metersphere-mcp"
New-Item -ItemType Directory -Force -Path $StageRoot | Out-Null

Copy-Item -Recurse $DistDir (Join-Path $StageRoot "dist")
Copy-Item (Join-Path $McpDir "package.json") $StageRoot
Copy-Item (Join-Path $McpDir "README.md") $StageRoot
Copy-Item (Join-Path $McpDir "INSTALL.md") $StageRoot
$Example = Join-Path $Root ".cursor\mcp.json.example"
if (Test-Path $Example) {
    Copy-Item $Example (Join-Path $StageRoot "mcp.json.example")
}

$ZipName = "metersphere-mcp-$Version.zip"
$ZipPath = Join-Path $OutDir $ZipName
if (Test-Path $ZipPath) { Remove-Item -Force $ZipPath }

Compress-Archive -Path $StageRoot -DestinationPath $ZipPath -Force

$Manifest = @{
    name        = "@midoo/metersphere-mcp"
    version     = $Version
    fileName    = $ZipName
    description = "MeterSphere MCP server package for Cursor / Claude Desktop"
    nodeEngine  = ">=18"
    installHint = "Unzip, run npm install --omit=dev, point mcp.json args to dist/index.js"
} | ConvertTo-Json -Depth 4

Set-Content -Path (Join-Path $OutDir "manifest.json") -Value $Manifest -Encoding UTF8

Remove-Item -Recurse -Force $Stage
Write-Host "Packed: $ZipPath"
Write-Host "Manifest: $(Join-Path $OutDir 'manifest.json')"
