<#
一键启动后端（邮件配置封装版）

用法：
  pwsh tools\start-backend.ps1                    # 首次运行会引导你选发件方式并生成 deploy\mail.env
  pwsh tools\start-backend.ps1 -Provider qq       # 临时切回 QQ 邮箱（会更新配置文件）
  pwsh tools\start-backend.ps1 -Reconfigure       # 重新配置发件方式
  pwsh tools\start-backend.ps1 -DryRun            # 只打印将要设置的环境变量，不启动
  pwsh tools\start-backend.ps1 -SkipInfra         # 跳过 docker 容器启动

说明：
  脚本按发件方式自动写入 host、port、HELO、STARTTLS 等 Spring Mail 配置，
  同时在当前进程注入同样的环境变量，IntelliJ 和命令行启动都能使用。
  该文件含密码，已被 .gitignore 忽略，不会进仓库。
#>
param(
    [ValidateSet('brevo', 'smtp2go', 'qq', 'mailpit')]
    [string]$Provider,
    [switch]$Reconfigure,
    [switch]$DryRun,
    [switch]$SkipInfra
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot 'deploy\mail.env'

$presets = @{
    brevo   = @{ label = 'Brevo（300 封/天，先到后台验证发件邮箱）'; host = 'smtp-relay.brevo.com'; port = 587; helo = 'brevo.com'; starttls = $true; auth = $true }
    smtp2go = @{ label = 'SMTP2GO（1000 封/月，2525 端口可绕封锁）'; host = 'mail.smtp2go.com'; port = 2525; helo = 'smtp2go.com'; starttls = $true; auth = $true }
    qq      = @{ label = 'QQ 邮箱（个人额度低，容易进垃圾箱）'; host = 'smtp.qq.com'; port = 587; helo = 'qq.com'; starttls = $true; auth = $true }
    mailpit = @{ label = '本地 Mailpit（不外发，http://localhost:8025 收信）'; host = 'localhost'; port = 1025; helo = 'kaoyan.local'; starttls = $false; auth = $false }
}
$providerKeys = @('brevo', 'smtp2go', 'qq', 'mailpit')

function Read-MailEnv {
    param([string]$Path)
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#') -or -not $text.Contains('=')) { continue }
        $key, $value = $text -split '=', 2
        $map[$key.Trim()] = $value.Trim()
    }
    return $map
}

function Write-MailEnv {
    param(
        [string]$Path,
        [string]$MailProvider,
        [string]$User,
        [string]$Secret,
        [string]$From,
        [hashtable]$Preset
    )
    $content = @(
        '# 发件配置：本文件含密码，已被 .gitignore 忽略，不入库',
        '# 可选 MAIL_PROVIDER：brevo / smtp2go / qq / mailpit',
        "MAIL_PROVIDER=$MailProvider",
        "MAIL_HOST=$($Preset.host)",
        "MAIL_PORT=$($Preset.port)",
        "MAIL_USERNAME=$User",
        "MAIL_PASSWORD=$Secret",
        "MAIL_SMTP_AUTH=$($Preset.auth.ToString().ToLowerInvariant())",
        "MAIL_STARTTLS=$($Preset.starttls.ToString().ToLowerInvariant())",
        "MAIL_HELO_HOST=$($Preset.helo)",
        "MAIL_FROM=$From",
        "MAIL_CONNECTION_TIMEOUT=10000",
        "MAIL_READ_TIMEOUT=10000",
        "MAIL_WRITE_TIMEOUT=10000"
    ) -join "`r`n"
    Set-Content -LiteralPath $Path -Value $content -Encoding utf8
    Write-Host ("已写入 " + $Path) -ForegroundColor DarkGray
}

function Select-Provider {
    param([string]$Default)
    Write-Host ''
    Write-Host '选择发件方式：' -ForegroundColor Cyan
    for ($i = 0; $i -lt $providerKeys.Count; $i++) {
        $key = $providerKeys[$i]
        Write-Host ("  {0}) {1}" -f ($i + 1), $presets[$key].label)
    }
    $answer = Read-Host ("输入序号，直接回车 = " + $Default)
    if ([string]::IsNullOrWhiteSpace($answer)) { return $Default }
    $index = 0
    if ([int]::TryParse($answer, [ref]$index) -and $index -ge 1 -and $index -le $providerKeys.Count) {
        return $providerKeys[$index - 1]
    }
    return $Default
}

# ---------- 读取或生成配置 ----------
$config = $null
if (Test-Path $envFile) {
    $config = Read-MailEnv $envFile
}

$needConfig = (-not $config) -or $Reconfigure
if (-not $needConfig -and $Provider -and $config['MAIL_PROVIDER'] -ne $Provider) {
    $needConfig = $true
}

if ($needConfig) {
    if ($DryRun) {
        $previewProvider = if ($Provider) { $Provider } else { 'mailpit' }
        $config = @{
            MAIL_PROVIDER = $previewProvider
            MAIL_USERNAME = 'preview@example.com'
            MAIL_PASSWORD = 'preview-only'
            MAIL_FROM     = 'preview@example.com'
        }
    }
    else {
        $defaultProvider = if ($Provider) { $Provider } elseif ($config -and $config['MAIL_PROVIDER']) { $config['MAIL_PROVIDER'] } else { 'brevo' }
        $chosen = Select-Provider $defaultProvider

        if ($chosen -eq 'mailpit') {
            Write-MailEnv $envFile $chosen '' '' 'no-reply@kaoyan.local' $presets[$chosen]
        }
        else {
            $defaultUser = if ($config -and $config['MAIL_USERNAME']) { $config['MAIL_USERNAME'] } else { '' }
            $prompt = if ($defaultUser) { "发件邮箱，直接回车 = $defaultUser" } else { '发件邮箱（也是登录 SMTP 的账号）' }
            $user = Read-Host $prompt
            if ([string]::IsNullOrWhiteSpace($user)) { $user = $defaultUser }
            if ([string]::IsNullOrWhiteSpace($user)) { throw '发件邮箱不能为空' }

            $secretPrompt = if ($chosen -eq 'qq') { 'QQ 邮箱授权码（不是登录密码）' } else { 'SMTP 密码 / API key' }
            $secret = Read-Host $secretPrompt
            if ([string]::IsNullOrWhiteSpace($secret)) { throw '密码或 key 不能为空' }

            $fromPrompt = "发件人地址，直接回车 = $user"
            $from = Read-Host $fromPrompt
            if ([string]::IsNullOrWhiteSpace($from)) { $from = $user }

            Write-MailEnv $envFile $chosen $user $secret $from $presets[$chosen]
        }
        $config = Read-MailEnv $envFile
    }
}

# ---------- 映射成 Spring 环境变量 ----------
$providerName = if ($config['MAIL_PROVIDER']) { $config['MAIL_PROVIDER'] } else { 'mailpit' }
if (-not $presets.ContainsKey($providerName)) {
    throw ("deploy\mail.env 里的 MAIL_PROVIDER 不认识：" + $providerName)
}
$preset = $presets[$providerName]
$mailFrom = if ($config['MAIL_FROM']) { $config['MAIL_FROM'] } else { $config['MAIL_USERNAME'] }

$envMap = [ordered]@{
    MAIL_HOST             = $preset.host
    MAIL_PORT             = [string]$preset.port
    MAIL_USERNAME         = if ($preset.auth) { [string]$config['MAIL_USERNAME'] } else { '' }
    MAIL_PASSWORD         = if ($preset.auth) { [string]$config['MAIL_PASSWORD'] } else { '' }
    MAIL_SMTP_AUTH        = if ($preset.auth) { 'true' } else { 'false' }
    MAIL_STARTTLS         = if ($preset.starttls) { 'true' } else { 'false' }
    MAIL_HELO_HOST        = $preset.helo
    MAIL_FROM             = $mailFrom
    KAOYAN_AUTH_MAIL_FROM = $mailFrom
}

Write-Host ''
Write-Host ("发件方式：" + $preset.label) -ForegroundColor Green
foreach ($key in $envMap.Keys) {
    Write-Host ("  {0}={1}" -f $key, $envMap[$key])
}

if ($DryRun) {
    Write-Host ''
    Write-Host '-DryRun：只打印配置，不启动后端。' -ForegroundColor DarkGray
    exit 0
}

# ---------- 让中间件先就绪 ----------
if (-not $SkipInfra) {
    Write-Host ''
    Write-Host '确保 MySQL / Redis / RabbitMQ / MinIO 已启动...' -ForegroundColor Cyan
    foreach ($name in @('kaoyan-mysql', 'kaoyan-redis', 'kaoyan-rabbitmq', 'kaoyan-minio')) {
        try {
            docker start $name 2>$null | Out-Null
        }
        catch {
            Write-Host ("  容器 " + $name + " 启动失败，跳过（可能需要先 docker compose up -d）") -ForegroundColor Yellow
        }
    }
    Start-Sleep -Seconds 6
}

# ---------- 8080 冲突处理 ----------
$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    Write-Host ''
    Write-Host ("端口 8080 已被 PID " + $listener.OwningProcess + " 占用（可能是之前没关的后端）") -ForegroundColor Yellow
    $answer = Read-Host '结束它并继续？(y/N)'
    if ($answer -eq 'y') {
        Stop-Process -Id $listener.OwningProcess -Force
        Start-Sleep -Seconds 2
    }
    else {
        Write-Host '已取消。' -ForegroundColor Yellow
        exit 1
    }
}

# ---------- 启动 ----------
foreach ($key in $envMap.Keys) {
    Set-Item -Path ("Env:" + $key) -Value $envMap[$key]
}

$backendDir = Join-Path $repoRoot 'backend'
Set-Location $backendDir
Write-Host ''
Write-Host '启动后端：mvn spring-boot:run' -ForegroundColor Green
mvn spring-boot:run
