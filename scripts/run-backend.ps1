# 由 start-all.ps1 调起的后端启动器（独立窗口运行）
[CmdletBinding()]
param(
    [string]$Profile = 'dev'
)
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$Host.UI.RawUI.WindowTitle = 'kaoyan-backend'
Set-Location -LiteralPath (Join-Path $Root 'backend')

Write-Host '==> mvn spring-boot:run (profile=' -NoNewline -ForegroundColor Cyan
Write-Host $Profile -NoNewline -ForegroundColor Cyan
Write-Host ')' -ForegroundColor Cyan

mvn spring-boot:run "-Dspring-boot.run.profiles=$Profile"
$code = $LASTEXITCODE
if ($code -ne 0) {
    Write-Host "后端启动失败（exit=$code），窗口保留供排查" -ForegroundColor Red
}
