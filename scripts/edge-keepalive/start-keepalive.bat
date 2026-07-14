@echo off
setlocal
cd /d "%~dp0"
set EDGE_CDP_URL=http://127.0.0.1:9222
set KEEPALIVE_INTERVAL_MIN=5
node keepalive.mjs
