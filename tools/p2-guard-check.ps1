# 一次性验证：鉴权链账号状态兜底（AccountStatusGuard）
# 目的：绕过 AdminService（不 kickout、不清缓存），直接改库把账号禁用，
#       观察旧 token 的后续请求是否会被鉴权链拦成 403 + 10013。

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
try { Add-Type -AssemblyName System.Net.Http -ErrorAction Stop } catch {}

$Base = 'http://localhost:8080'
$JavaExe = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.1\jbr\bin\java.exe'
$JdbcJar = "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar"
$SqlSrc  = Join-Path $env:TEMP 'kaoyan-db\Sql.java'

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
function Invoke-Raw($method, $url, $headers, $body, $contentType) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    try {
        $m = New-Object System.Net.Http.HttpMethod($method)
        $req = New-Object System.Net.Http.HttpRequestMessage($m, $url)
        if ($headers) { foreach ($k in $headers.Keys) { [void]$req.Headers.Add($k, [string]$headers[$k]) } }
        if ($body) { $req.Content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, $contentType) }
        $resp = $client.SendAsync($req).GetAwaiter().GetResult()
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult() }
    } catch {
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    } finally { $client.Dispose() }
}
function Http-Json($method, $url, $headers, $bodyObj) {
    $json = if ($bodyObj) { $bodyObj | ConvertTo-Json } else { $null }
    $raw = Invoke-Raw $method $url $headers $json 'application/json'
    try { return ($raw.Body | ConvertFrom-Json) } catch { return [pscustomobject]@{ code = -1; message = $raw.Body } }
}
function Login($account, $password) {
    $cap = Invoke-RestMethod "$Base/api/v1/auth/captcha"
    $key = $cap.data.captcha_key
    $answer = Redis-Get "auth:captcha:$key"
    $body = @{ login_type = 'PASSWORD'; account = $account; password = $password; captcha_key = $key; captcha_code = $answer }
    return Http-Json 'POST' "$Base/api/v1/auth/login" $null $body
}
function Sql($sql) {
    if (-not (Test-Path $SqlSrc)) { throw '缺少 Sql.java 查询器' }
    return (& $JavaExe -cp $JdbcJar $SqlSrc $sql) -join "`n"
}
function Me($token) {
    $r = Invoke-Raw 'GET' "$Base/api/v1/auth/me" @{ Authorization = "Bearer $token" } $null $null
    $code = -1
    try { $code = ($r.Body | ConvertFrom-Json).code } catch {}
    return [pscustomobject]@{ Status = $r.Status; Code = $code }
}

$admin = Login 'admin' 'Admin@123456'
$adminToken = $admin.data.token
$stu = Login 'student1' 'Student@123456'
$stuToken = $stu.data.token
$idOut = Sql "SELECT id FROM sys_user WHERE username='student1'"
$idLine = ($idOut -split "`n" | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -First 1)
$stuId = [int]$idLine.Trim()

Write-Host "`n学生 id=$stuId，基线请求：" -ForegroundColor Cyan
$base0 = Me $stuToken
Write-Host ("  /auth/me -> HTTP {0} code={1}" -f $base0.Status, $base0.Code)

Write-Host "`n绕过 AdminService，直接改库禁用（不踢人、不清缓存）..." -ForegroundColor Yellow
Sql "UPDATE sys_user SET status=0 WHERE id=$stuId" | Out-Null

$hit = $null
for ($i = 1; $i -le 25; $i++) {
    $r = Me $stuToken
    Write-Host ("  第 {0,2} 次（{1,3}s）：HTTP {2} code={3}" -f $i, (($i - 1) * 3), $r.Status, $r.Code)
    if ($r.Code -eq 10013) { $hit = $r; break }
    Start-Sleep -Seconds 3
}

Write-Host "`n复原：走后台接口改回 status=1（会主动失效守卫缓存）..." -ForegroundColor Yellow
$fix = Http-Json 'PUT' "$Base/api/v1/admin/users/$stuId/status" @{ Authorization = "Bearer $adminToken" } @{ status = 1 }
Write-Host ("  PUT -> code={0} msg={1}" -f $fix.code, $fix.message)
Start-Sleep -Seconds 1
$after = Me $stuToken
Write-Host ("  复原后 /auth/me -> HTTP {0} code={1}" -f $after.Status, $after.Code)

Write-Host ""
if ($hit) {
    Write-Host "结论：旧 token 在缓存过期后被鉴权链拦成 403 + 10013 —— 兜底生效（kickout 没参与）" -ForegroundColor Green
} else {
    Write-Host "结论：25 次（75 秒）内未拦到 10013 —— 兜底可能未生效，需要检查" -ForegroundColor Red
}
