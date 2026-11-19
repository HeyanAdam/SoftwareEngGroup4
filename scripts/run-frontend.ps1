# 由 start-all.ps1 调起的前端启动器（独立窗口运行）
[CmdletBinding()]
param(
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$Host.UI.RawUI.WindowTitle = 'kaoyan-frontend'
Set-Location -LiteralPath (Join-Path $Root 'frontend')

if ($Install -or (-not (Test-Path -LiteralPath 'node_modules'))) {
    Write-Host '==> npm install' -ForegroundColor Cyan
    npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'npm install 失败，窗口保留供排查' -ForegroundColor Red
        return
    }
}

Write-Host '==> npm run dev' -ForegroundColor Cyan
npm run dev
