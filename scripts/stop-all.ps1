# ==========================================================================
#  一键停止：停掉 Docker 基础设施容器
#
#  用法：
#    powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
#    或直接双击根目录的 stop-all.cmd
#
#  参数：
#    -Down         同时删除容器（数据卷保留，MySQL/Redis/RabbitMQ 数据不丢）
#    -IncludeApps  连同监听 8080/5173 的后端、前端进程一起结束
# ==========================================================================
[CmdletBinding()]
param(
    [switch]$Down,
    [switch]$IncludeApps
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$Root = Split-Path -Parent $ScriptDir

function Write-Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn2([string]$msg) { Write-Host "  [!!] $msg" -ForegroundColor Yellow }

if ($IncludeApps) {
    Write-Step '结束占用 8080 / 5173 端口的进程'
    foreach ($port in @(8080, 5173)) {
        $pids = @()
        try {
            $pids = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop |
                Select-Object -ExpandProperty OwningProcess -Unique)
        } catch {
            $pids = @()
        }
        if ($pids.Count -eq 0) {
            Write-Warn2 "端口 $port 没有监听进程"
            continue
        }
        foreach ($targetPid in $pids) {
            $proc = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
            if ($null -eq $proc) { continue }
            Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
            Write-Ok "已结束 $($proc.ProcessName) (PID $targetPid, 端口 $port)"
        }
    }
}

Write-Step '停止 Docker 容器'
Push-Location $Root
try {
    if ($Down) {
        & docker compose --env-file './deploy/.env' -f './deploy/docker-compose.yml' down
        Write-Ok '容器已删除（数据卷保留）'
    } else {
        & docker compose --env-file './deploy/.env' -f './deploy/docker-compose.yml' stop
        Write-Ok '容器已停止（docker compose up -d 可再次拉起）'
    }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host '如果后端/前端仍在独立窗口运行，直接在窗口里 Ctrl+C 关闭。' -ForegroundColor DarkGray
