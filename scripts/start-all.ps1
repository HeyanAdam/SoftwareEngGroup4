# ==========================================================================
#  一键启动：Docker 基础设施 -> 后端 Spring Boot -> 前端 Vite
#
#  用法（在仓库根目录或任意目录均可）：
#    powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
#    或直接双击根目录的 start-all.cmd
#
#  可选参数：
#    -SkipInfra             不启动 Docker 容器（本地已有中间件时用）
#    -SkipBackend           不启动后端
#    -SkipFrontend          不启动前端
#    -ForceInstall          前端强制重新 npm install
#    -InfraTimeoutSeconds N 等待容器就绪的秒数（默认 180）
#    -ReadyTimeoutSeconds N 等待 8080/5173 端口就绪的秒数（默认 90）
# ==========================================================================
[CmdletBinding()]
param(
    [switch]$SkipInfra,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$ForceInstall,
    [int]$InfraTimeoutSeconds = 180,
    [int]$ReadyTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$Root = Split-Path -Parent $ScriptDir
$DeployDir = Join-Path $Root 'deploy'
$ComposeFile = Join-Path $DeployDir 'docker-compose.yml'
$EnvFile = Join-Path $DeployDir '.env'

$BackendPort = 8080
$FrontendPort = 5173

function Write-Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn2([string]$msg) { Write-Host "  [!!] $msg" -ForegroundColor Yellow }
function Write-Err([string]$msg) { Write-Host "  [XX] $msg" -ForegroundColor Red }

function Test-CommandExists([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { return $null }
    return $cmd.Source
}

function Test-PortListening([int]$port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop
        return ($null -ne $conn)
    } catch {
        return $false
    }
}

function Wait-Healthy([string]$name, [int]$timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $status = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $name 2>$null
        if ($status -eq 'healthy') { return 'healthy' }
        if ($status -eq 'unhealthy' -or $status -eq 'exited' -or $status -eq 'dead') { return $status }
        Start-Sleep -Seconds 3
    }
    return 'timeout'
}

function Wait-Port([int]$port, [int]$timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening $port) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

# ---------------------------------------------------------------- 基础设施
if (-not $SkipInfra) {
    Write-Step '检查 Docker'
    $docker = Test-CommandExists 'docker'
    if (-not $docker) {
        Write-Err '未找到 docker 命令，请先安装 Docker Desktop 并重新打开终端'
        exit 1
    }

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Warn2 'Docker 引擎未运行，尝试启动 Docker Desktop ...'
        $candidates = @(
            (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
            (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'),
            (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe')
        )
        $exe = $null
        foreach ($c in $candidates) {
            if (Test-Path -LiteralPath $c) { $exe = $c; break }
        }
        if ($null -eq $exe) {
            Write-Err '找不到 Docker Desktop 可执行文件，请手工启动 Docker Desktop 后重试'
            exit 1
        }
        Start-Process -FilePath $exe | Out-Null

        $deadline = (Get-Date).AddSeconds(180)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 5
            & docker info *> $null
            if ($LASTEXITCODE -eq 0) { break }
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Err 'Docker Desktop 启动超时，请手工确认 Docker 已就绪后重试'
            exit 1
        }
        Write-Ok 'Docker 引擎已就绪'
    } else {
        Write-Ok 'Docker 引擎运行中'
    }

    if (-not (Test-Path -LiteralPath $EnvFile)) {
        Copy-Item -LiteralPath (Join-Path $DeployDir '.env.example') -Destination $EnvFile
        Write-Warn2 'deploy/.env 不存在，已从 .env.example 复制；如需调用大模型请填入 LLM_API_KEY / EMBEDDING_API_KEY'
    }

    Write-Step '启动基础设施容器（MySQL / Redis / RabbitMQ / Mailpit）'
    Push-Location $Root
    try {
        & docker compose --env-file './deploy/.env' -f './deploy/docker-compose.yml' up -d
        if ($LASTEXITCODE -ne 0) {
            Write-Err 'docker compose up 失败，请查看上方输出'
            exit 1
        }
    } finally {
        Pop-Location
    }

    Write-Step "等待容器就绪（最长 $InfraTimeoutSeconds 秒）"
    $failed = $false
    foreach ($name in @('kaoyan-mysql', 'kaoyan-redis', 'kaoyan-rabbitmq')) {
        $status = Wait-Healthy $name $InfraTimeoutSeconds
        if ($status -eq 'healthy') {
            Write-Ok "$name 就绪"
        } else {
            Write-Err "$name 状态异常：$status（可执行 docker logs $name 排查）"
            $failed = $true
        }
    }
    if ($failed) { exit 1 }
}

# ---------------------------------------------------------------- 后端
$backendStarted = $false
if (-not $SkipBackend) {
    Write-Step '启动后端（Spring Boot，profile=dev）'
    if (Test-PortListening $BackendPort) {
        Write-Warn2 "端口 $BackendPort 已被占用，跳过后端启动（如需重启请先关闭占用进程）"
    } else {
        $mvn = Test-CommandExists 'mvn'
        if (-not $mvn) {
            Write-Err '未找到 mvn，请确认 Maven 已安装且已加入 PATH'
            exit 1
        }
        $backendLauncher = Join-Path $ScriptDir 'run-backend.ps1'
        Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$backendLauncher`"") `
            -WorkingDirectory (Join-Path $Root 'backend') | Out-Null
        $backendStarted = $true
        Write-Ok '后端已在新窗口启动（首次启动需下载依赖，请耐心等待）'
    }
}

# ---------------------------------------------------------------- 前端
$frontendStarted = $false
if (-not $SkipFrontend) {
    Write-Step '启动前端（Vite）'
    if (Test-PortListening $FrontendPort) {
        Write-Warn2 "端口 $FrontendPort 已被占用，跳过前端启动（如需重启请先关闭占用进程）"
    } else {
        $npm = Test-CommandExists 'npm'
        if (-not $npm) {
            Write-Err '未找到 npm，请安装 Node.js 并重新打开终端'
            exit 1
        }
        $needInstall = $ForceInstall -or (-not (Test-Path -LiteralPath (Join-Path $Root 'frontend\node_modules')))
        $frontendLauncher = Join-Path $ScriptDir 'run-frontend.ps1'
        $frontendArgs = @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$frontendLauncher`"")
        if ($needInstall) { $frontendArgs += '-Install' }
        Start-Process -FilePath 'powershell.exe' `
            -ArgumentList $frontendArgs `
            -WorkingDirectory (Join-Path $Root 'frontend') | Out-Null
        $frontendStarted = $true
        Write-Ok '前端已在新窗口启动'
    }
}

# ---------------------------------------------------------------- 就绪确认
if ($backendStarted -or $frontendStarted) {
    Write-Step "等待服务端口就绪（最长 $ReadyTimeoutSeconds 秒）"
    if ($backendStarted) {
        if (Wait-Port $BackendPort $ReadyTimeoutSeconds) { Write-Ok "后端 $BackendPort 已监听" }
        else { Write-Warn2 "后端 $BackendPort 尚未就绪，请查看 kaoyan-backend 窗口的日志" }
    }
    if ($frontendStarted) {
        if (Wait-Port $FrontendPort ($ReadyTimeoutSeconds / 2)) { Write-Ok "前端 $FrontendPort 已监听" }
        else { Write-Warn2 "前端 $FrontendPort 尚未就绪，请查看 kaoyan-frontend 窗口的日志" }
    }
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' 启动完成，访问地址：' -ForegroundColor Cyan
Write-Host "  前端首页      http://localhost:$FrontendPort"
Write-Host "  后端健康检查  http://localhost:$BackendPort/api/v1/health"
Write-Host "  接口文档      http://localhost:$BackendPort/swagger-ui/index.html"
Write-Host '  RabbitMQ 管理 http://localhost:15672'
Write-Host '  Mailpit       http://localhost:8025'
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' 停止：双击 stop-all.cmd，或 scripts\stop-all.ps1' -ForegroundColor DarkGray
