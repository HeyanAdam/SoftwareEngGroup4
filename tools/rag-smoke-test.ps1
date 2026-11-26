<#
RAG 端到端冒烟测试（任务A 自测用，可反复运行）

覆盖链路：
  验证码/登录 → 文件上传存 MinIO → 建知识库 → 上传文档并异步解析切分
  → 千问 Embedding 向量化写 Milvus → 大模型流式问答（SSE 返回 references/delta/done）

用法（在仓库根目录执行，需要 pwsh 7）：
  pwsh tools\rag-smoke-test.ps1
  pwsh tools\rag-smoke-test.ps1 -Account admin -Password 'Admin@123456'
  pwsh tools\rag-smoke-test.ps1 -SkipChat          # 只验证到向量入库

前置：MySQL / Redis / RabbitMQ / MinIO / Milvus 容器已起，后端已运行在 8080，
      deploy\.env 里 LLM_API_KEY、EMBEDDING_API_KEY 已填。
#>
param(
    [string]$Base = 'http://localhost:8080',
    [string]$Account = 'admin',
    [string]$Password = 'Admin@123456',
    [string]$RedisContainer = 'kaoyan-redis',
    [string]$MinioContainer = 'minio',
    [string]$MySQLContainer = 'kaoyan-mysql',
    [string]$MinioBucket = 'kaoyan-files',
    [switch]$SkipChat
)

# 用 continue：单步失败不中断整体冒烟，最后统一汇总
$ErrorActionPreference = 'Continue'
$script:pass = 0; $script:fail = 0

# docker 不在 PATH 时回退到 Docker Desktop 自带的 docker.exe
$docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $docker) { $docker = 'D:\Docker\Desktop\resources\bin\docker.exe' }

function Step($name, $ok, $detail) {
    if ($ok) { $script:pass++; Write-Host ("  [OK]   " + $name + "  " + $detail) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  [FAIL] " + $name + "  " + $detail) -ForegroundColor Red }
}

function Json($method, $url, $headers, $obj) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 60 }
    if ($headers) { $p.Headers = $headers }
    if ($obj) { $p.ContentType = 'application/json'; $p.Body = ($obj | ConvertTo-Json -Depth 10 -Compress) }
    try {
        $r = Invoke-RestMethod @p
        return [pscustomobject]@{ Status = 200; Data = $r }
    }
    catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $code; Data = $null; Error = $_.ErrorDetails.Message }
    }
}

function RedisGet($key) {
    $raw = & $docker exec $RedisContainer redis-cli --raw GET $key 2>$null
    if (-not $raw) { return $null }
    return ($raw -join '').Trim()
}

function MySqlScalar($sql) {
    $rows = & $docker exec $MySQLContainer mysql -ukaoyan -pkaoyan123 -N -e "use kaoyan_ai; $sql" 2>$null
    $hit = $rows | Where-Object { $_ -match '^\d+' } | Select-Object -Last 1
    if (-not $hit) { return 0 }
    return [int]$hit
}

Write-Host "`n=== 1. 验证码 + 登录 ===" -ForegroundColor Cyan
$cap = (Json 'GET' "$Base/api/v1/auth/captcha" $null $null).Data
$capKey = $cap.data.captcha_key
$capAnswer = RedisGet "auth:captcha:$capKey"
Step '取验证码（从 Redis 读答案）' ([bool]$capKey -and [bool]$capAnswer) "key=$capKey code=$capAnswer"

$login = Json 'POST' "$Base/api/v1/auth/login" $null @{
    login_type = 'PASSWORD'; account = $Account; password = $Password
    captcha_key = $capKey; captcha_code = $capAnswer
}
$tok = $login.Data.data.token
$auth = @{ Authorization = "Bearer $tok" }
Step '账号登录' ([bool]$tok) ("roles=" + ($login.Data.data.roles -join '/'))

Write-Host "`n=== 2. 文件上传（MinIO 对象存储） ===" -ForegroundColor Cyan
$tmp = Join-Path $env:TEMP ('rag-smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 8) + '.txt')
@'
2026 考研复试流程说明
一、初试成绩公布后，考生需在研究生院官网查询复试名单并确认资格。
二、复试包含专业课笔试、英语口语面试、综合面试三部分，占总成绩 40%。
三、复试前需提交本科学历证明、成绩单和思想政治素质考核表。
四、复试合格名单公示后进入拟录取与体检环节。
'@ | Set-Content -LiteralPath $tmp -Encoding utf8

try {
    $up = Invoke-RestMethod -Uri "$Base/api/v1/files?scene=STUDY_MATERIAL" -Method Post -Headers $auth `
        -Form @{ file = Get-Item -LiteralPath $tmp } -TimeoutSec 90
    Step '文件上传接口返回对象' ($up.code -eq 0) ("fileId=" + $up.data.id + " objectKey=" + $up.data.objectKey)
    $objectKey = $up.data.objectKey
}
catch {
    Step '文件上传接口返回对象' $false ($_.Exception.Message)
    $objectKey = $null
}

if ($objectKey) {
    $prefix = ($objectKey -split '/')[0]
    $listed = & $docker exec $MinioContainer ls "/data/$MinioBucket/$prefix" 2>$null
    Step 'MinIO 桶内落盘' ([bool]($listed -and ($listed -join '') -ne '')) ("bucket=$MinioBucket prefix=$prefix")
}

Write-Host "`n=== 3. 知识库 + 文档解析 + 向量化 ===" -ForegroundColor Cyan
$kb = (Json 'POST' "$Base/api/v1/knowledge-bases" $auth @{ name = '冒烟测试知识库'; description = 'RAG 端到端自测' }).Data
Step '创建知识库' ($kb.code -eq 0) ("kbId=" + $kb.data.id)
$kbId = $kb.data.id

try {
    $doc = Invoke-RestMethod -Uri "$Base/api/v1/knowledge-bases/$kbId/documents" -Method Post -Headers $auth `
        -Form @{ file = Get-Item -LiteralPath $tmp } -TimeoutSec 90
    $docId = $doc.data.id
    Step '上传文档（触发异步解析）' ($doc.code -eq 0) ("docId=$docId status=" + $doc.data.status)
}
catch {
    Step '上传文档（触发异步解析）' $false $_.Exception.Message
    $docId = $null
}

if ($docId) {
    Write-Host '  等待解析/切分/向量化完成（最多 90 秒）...' -ForegroundColor DarkGray
    $status = $null
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        $status = (Json 'GET' "$Base/api/v1/knowledge-documents/$docId" $auth $null).Data.data.status
        if ($status -eq 'READY' -or $status -eq 'FAILED') { break }
    }
    Step '文档状态 READY' ($status -eq 'READY') "status=$status"

    $chunks = MySqlScalar "select count(*) from kb_document_chunk where doc_id=$docId;"
    Step '分片写入 MySQL' ($chunks -gt 0) "chunks=$chunks"

    $vecs = MySqlScalar "select count(*) from kb_document_chunk where doc_id=$docId and milvus_id is not null;"
    Step '向量写入 Milvus（milvus_id 回填）' ($vecs -gt 0) "vectors=$vecs"
}

Write-Host "`n=== 4. RAG 流式问答（SSE） ===" -ForegroundColor Cyan
if ($SkipChat) {
    Write-Host '  已跳过（-SkipChat）' -ForegroundColor DarkGray
}
elseif ($kbId) {
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$Base/api/v1/chat/stream")
    $req.Headers.TryAddWithoutValidation('Authorization', "Bearer $tok") | Out-Null
    $payload = @{ conversationId = $null; kbId = $kbId; question = '复试包含哪几部分？占总成绩多少？'; clientMessageId = [guid]::NewGuid().ToString() }
    $req.Content = [System.Net.Http.StringContent]::new(($payload | ConvertTo-Json -Compress), [System.Text.Encoding]::UTF8, 'application/json')

    $resp = $client.SendAsync($req, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    $reader = [System.IO.StreamReader]::new($resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult())
    $events = @()
    $cur = ''
    $script:answer = ''
    $errText = ''
    while (-not $reader.EndOfStream) {
        $line = $reader.ReadLine()
        if ($line -like 'event:*') { $cur = ($line -replace 'event:', '').Trim(); $events += $cur }
        elseif ($line -like 'data:*') {
            $raw = ($line -replace 'data:', '').Trim()
            try { $d = $raw | ConvertFrom-Json } catch { continue }
            if ($cur -eq 'delta' -and $d.content) { $script:answer = $script:answer + $d.content }
            if ($cur -eq 'error') { $errText = $raw }
        }
    }
    $reader.Dispose()

    $hasRef = $events -contains 'references'
    $hasDelta = $events -contains 'delta'
    $hasDone = $events -contains 'done'
    Step 'SSE 依次返回 references/delta/done' ($hasRef -and $hasDelta -and $hasDone) "ref=$hasRef delta=$hasDelta done=$hasDone"
    if ($errText) { Write-Host ('  error 事件：' + $errText) -ForegroundColor Yellow }

    $answer = $script:answer
    if ($answer -and $answer.Length -gt 0) {
        Step '模型返回了回答正文' $true ("长度=" + $answer.Length)
        Write-Host ('  回答：' + $answer.Substring(0, [Math]::Min(200, $answer.Length))) -ForegroundColor DarkGray
    }
    else {
        Step '模型返回了回答正文' $false 'delta 为空，检查 LLM_API_KEY / 余额'
    }
}

Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue

Write-Host ""
Write-Host ("通过 $($script:pass) 项，失败 $($script:fail) 项") -ForegroundColor ($(if ($script:fail -eq 0) { 'Green' } else { 'Yellow' }))
exit ([int]($script:fail -gt 0))

