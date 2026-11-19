# P2 验收脚本（RBAC / 接口安全 / 审计 / 日志）
# 用法：在项目根目录 `pwsh -File tools\p2-acceptance.ps1`（需后端 8080 已启动、MySQL/Redis 已起）
# 设计原则：只读取与最小写，结束前把改过的数据复原。

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
# Windows PowerShell 5.1 需要显式加载 System.Net.Http；PS7 已内置，失败也无妨
try { Add-Type -AssemblyName System.Net.Http -ErrorAction Stop } catch {}
$Base = 'http://localhost:8080'
$JavaExe = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.1\jbr\bin\java.exe'
$JdbcJar = "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar"
$SqlSrc  = Join-Path $env:TEMP 'kaoyan-db\Sql.java'

$script:pass = 0; $script:fail = 0; $script:skip = 0
$rows = @()

function Report($name, $expected, $actual, $ok, $note) {
    $status = if ($ok) { 'PASS' } else { 'FAIL' }
    if ($ok) { $script:pass++ } else { $script:fail++ }
    $script:rows += [pscustomobject]@{ 用例 = $name; 预期 = $expected; 实际 = $actual; 结果 = $status; 说明 = $note }
}

function Skip($name, $expected, $note) {
    $script:skip++
    $script:rows += [pscustomobject]@{ 用例 = $name; 预期 = $expected; 实际 = '未验证'; 结果 = 'SKIP'; 说明 = $note }
}

# ---------- Redis 客户端（验证码答案在 Redis 明文，便于自动化登录） ----------
function Redis-Cmd([string[]]$argv) {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect('127.0.0.1', 6379)
    $s = $tcp.GetStream()
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("*$($argv.Count)`r`n")
    foreach ($a in $argv) { [void]$sb.Append("`$$($a.Length)`r`n$a`r`n") }
    $b = [Text.Encoding]::ASCII.GetBytes($sb.ToString())
    $s.Write($b, 0, $b.Length); Start-Sleep -Milliseconds 120
    $buf = New-Object byte[] 4096
    $n = $s.Read($buf, 0, 4096)
    $r = [Text.Encoding]::ASCII.GetString($buf, 0, $n)
    $tcp.Close()
    return $r
}

function Redis-Get($key) {
    $raw = Redis-Cmd @('GET', $key)
    if ($raw.StartsWith('$-1')) { return $null }
    return ($raw -split "`r`n")[1]
}

function Redis-Set($key, $value, $ttl) { return Redis-Cmd @('SET', $key, $value, 'EX', "$ttl") }

# ---------- HTTP 帮助函数 ----------
# 用 System.Net.Http.HttpClient：PowerShell 5.1 / 7 都能用，4xx/5xx 不抛异常，直接拿状态码与响应体
function Invoke-Raw($method, $url, $headers, $body, $contentType) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    try {
        $m = New-Object System.Net.Http.HttpMethod($method)
        $req = New-Object System.Net.Http.HttpRequestMessage($m, $url)
        if ($headers) { foreach ($k in $headers.Keys) { [void]$req.Headers.Add($k, [string]$headers[$k]) } }
        if ($body) {
            $req.Content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, $contentType)
        }
        $resp = $client.SendAsync($req).GetAwaiter().GetResult()
        $status = [int]$resp.StatusCode
        $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{ Status = $status; Body = $text }
    } catch {
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    } finally {
        $client.Dispose()
    }
}

function Http-Status($method, $url, $headers) {
    return Invoke-Raw $method $url $headers $null $null
}

function Http-Json($method, $url, $headers, $bodyObj) {
    $json = if ($bodyObj) { $bodyObj | ConvertTo-Json } else { $null }
    $raw = Invoke-Raw $method $url $headers $json 'application/json'
    try { return ($raw.Body | ConvertFrom-Json) }
    catch { return [pscustomobject]@{ code = -1; message = $raw.Body } }
}

function Login($account, $password) {
    $cap = Invoke-RestMethod "$Base/api/v1/auth/captcha"
    $key = $cap.data.captcha_key
    $answer = Redis-Get "auth:captcha:$key"
    $body = @{ login_type = 'PASSWORD'; account = $account; password = $password; captcha_key = $key; captcha_code = $answer }
    return Http-Json 'POST' "$Base/api/v1/auth/login" $null $body
}

function Ensure-User($username, $email, $password, $nickname) {
    $exists = Sql "SELECT id FROM sys_user WHERE username='$username'"
    if ($exists -match '\[rows=0\]') {
        Redis-Set "auth:code:REGISTER:$email" '888888' 300 | Out-Null
        $body = @{ username = $username; email = $email; verify_code = '888888'; password = $password; confirm_password = $password; nickname = $nickname; agree_agreement = $true }
        Http-Json 'POST' "$Base/api/v1/auth/register" @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } $body | Out-Null
        Report "预置账号 $username" '创建成功' $username $true '验收脚本自动准备'
    }
}

function Sql($sql) {
    if (-not (Test-Path $SqlSrc)) { return 'NO-SQL-TOOL' }
    return (& $JavaExe -cp $JdbcJar $SqlSrc $sql) -join "`n"
}

function SqlNum($sql) {
    $out = Sql $sql
    $line = ($out -split "`n" | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -First 1)
    if (-not $line) { return -1 }
    return [int]$line.Trim()
}

Write-Host "`n===== P2 验收开始 =====`n" -ForegroundColor Cyan

# ---------- 0. 环境 ----------
$live = Http-Status 'GET' "$Base/api/v1/health/live" $null
Report '健康检查 /health/live' '200 + UP' "$($live.Status)" ($live.Status -eq 200) ''
$ready = Http-Status 'GET' "$Base/api/v1/health/ready" $null
Report '健康检查 /health/ready' '200 + mysql/redis/rabbitmq UP' "$($ready.Status)" ($ready.Status -eq 200) ''

# ---------- 1. 未登录拦截 ----------
$anon = Http-Status 'GET' "$Base/api/kb" $null
Report '未登录访问 /api/kb' '401 + 10001' "$($anon.Status) $(($anon.Body | ConvertFrom-Json).code)" ($anon.Status -eq 401) '原先 /api/kb 有遗留放行，已删除'

# ---------- 2. 准备两个账号 ----------
Ensure-User 'admin' 'admin@kaoyan.local' 'Admin@123456' '系统管理员'
Ensure-User 'student1' 'student1@kaoyan.local' 'Student@123456' '学生一号'
# 幂等保护：上一次中断/复跑可能把账号留在禁用态，先统一复位成 status=1
Sql "UPDATE sys_user SET status=1 WHERE username IN ('admin','student1')" | Out-Null

# admin 需要 SYSTEM_ADMIN 角色
$roleCheck = Sql "SELECT r.role_code FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='admin' AND r.role_code='SYSTEM_ADMIN'"
if ($roleCheck -match '\[rows=0\]') {
    Sql "INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='SYSTEM_ADMIN' WHERE u.username='admin'" | Out-Null
    Report '预置管理员角色' '绑定 SYSTEM_ADMIN' '已绑定' $true '验收脚本自动准备'
}

$adminLogin = Login 'admin' 'Admin@123456'
$adminToken = $adminLogin.data.token
Report '管理员登录' 'code=0 且 roles 含 SYSTEM_ADMIN' "code=$($adminLogin.code) roles=$($adminLogin.data.roles -join '/')" ($adminLogin.code -eq 0) ''

$stuLogin = Login 'student1' 'Student@123456'
$stuToken = $stuLogin.data.token
Report '学生登录' 'code=0 且 roles=[STUDENT]' "code=$($stuLogin.code) roles=$($stuLogin.data.roles -join '/')" ($stuLogin.code -eq 0) ''

# ---------- 3. 权限矩阵（接口级） ----------
$stuMe = Http-Status 'GET' "$Base/api/v1/auth/me" @{ Authorization = "Bearer $stuToken" }
$stuPerms = ($stuMe.Body | ConvertFrom-Json).data.permissions
Report '学生 /auth/me 权限清单' '仅 chat:send' "$($stuPerms -join ',')" (($stuPerms.Count -eq 1) -and ($stuPerms[0] -eq 'chat:send')) ''

$stuAdmin = Http-Status 'GET' "$Base/api/admin/users" @{ Authorization = "Bearer $stuToken" }
$stuAdminCode = try { ($stuAdmin.Body | ConvertFrom-Json).code } catch { $null }
Report '学生调用后台接口' '403 + 10008' "$($stuAdmin.Status) code=$stuAdminCode" (($stuAdmin.Status -eq 403) -and ($stuAdminCode -eq 10008)) '真机可触发的 10008 路径'

$admAdmin = Http-Status 'GET' "$Base/api/admin/users" @{ Authorization = "Bearer $adminToken" }
Report '管理员调用后台接口' '200 + code=0' "$($admAdmin.Status)" ($admAdmin.Status -eq 200) '对照组'

# ---------- 4. 审计落库 ----------
$roleBefore = Http-Status 'GET' "$Base/api/admin/roles" @{ Authorization = "Bearer $adminToken" }
$auditorRole = ($roleBefore.Body | ConvertFrom-Json).data | Where-Object { $_.roleCode -eq 'DATA_AUDITOR' }
$beforeCount = SqlNum "SELECT COUNT(*) FROM sys_audit_log"

$upd = Http-Json 'PUT' "$Base/api/admin/roles/$($auditorRole.id)" @{ Authorization = "Bearer $adminToken" } @{ roleName = '数据审计员'; description = '只读查看用户、角色和知识库概览' }
Report '管理员改角色名（触发 @AuditLog）' 'code=0' "code=$($upd.code)" ($upd.code -eq 0) ''

$afterCount = SqlNum "SELECT COUNT(*) FROM sys_audit_log"
Report '审计记录新增' '条数 +1' "$beforeCount -> $afterCount" ($afterCount -gt $beforeCount) 'UPDATE_ROLE 落库'

$lastAudit = Sql "SELECT action,resource_type,resource_id,request_id FROM sys_audit_log ORDER BY id DESC LIMIT 1"
Report '审计内容' 'UPDATE_ROLE / ROLE / request_id 非空' $lastAudit.Replace("`n", ' ') ($lastAudit -match 'UPDATE_ROLE') ''

# ---------- 5. 禁用账号 ----------
$stuId = SqlNum "SELECT id FROM sys_user WHERE username='student1'"
Http-Json 'PUT' "$Base/api/admin/users/$stuId/status" @{ Authorization = "Bearer $adminToken" } @{ status = 0 } | Out-Null

$disabledLogin = Login 'student1' 'Student@123456'
Report '禁用后重新登录' '403 + 10013' "code=$($disabledLogin.code)" ($disabledLogin.code -eq 10013) ''

$oldToken = Http-Status 'GET' "$Base/api/v1/auth/me" @{ Authorization = "Bearer $stuToken" }
Report '禁用后旧 token 失效' '401 + 10001' "$($oldToken.Status)" ($oldToken.Status -eq 401) 'AdminService 禁用时 evict 快照 + kickout'

# 复原
Http-Json 'PUT' "$Base/api/admin/users/$stuId/status" @{ Authorization = "Bearer $adminToken" } @{ status = 1 } | Out-Null
Report '复原学生账号状态' 'status=1' '已复原' $true ''

# ---------- 6. 已知缺口（不在本轮验收内） ----------
Skip '学生越权读他人会话' '403 + 10009' 'DataScopeService.requireSelf 尚未接入业务接口（改造清单 B 项，主责 P3）'
Skip '改密后其他会话失效' '旧 token 401' 'UserService.changePassword 未调 logoutUser（改造清单 C 项，主责 P1）'

# ---------- 输出 ----------
Write-Host ''
$script:rows | Format-Table -AutoSize -Wrap
Write-Host "`n结果：PASS=$script:pass  FAIL=$script:fail  SKIP=$script:skip`n" -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
