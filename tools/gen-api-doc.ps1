# 生成《接口文档（当前实现）》：从运行中的后端拉 OpenAPI，并交叉源码扫描权限注解/审计注解
# 用法：后端 8080 已启动时，在项目根目录执行 .\tools\gen-api-doc.ps1

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$SrcRoot  = Join-Path $RepoRoot 'backend\src\main\java\com\group4\kaoyan'
$OutFile  = Join-Path $RepoRoot 'docs\00_项目管理\接口文档_当前实现.md'
$Base     = 'http://localhost:8080'

# ---------- 1. 拉 OpenAPI ----------
$spec = (Invoke-WebRequest -Uri "$Base/v3/api-docs" -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
$paths = @{}
foreach ($p in $spec.paths.PSObject.Properties) { $paths[$p.Name] = $p.Value }

# ---------- 2. 扫描控制器：路径 → 权限码 / 审计 action ----------
$map = @{}   # key = "METHOD path"（路径已去除类级前缀占位），value = @{perm; audit; ctrl}

Get-ChildItem $SrcRoot -Recurse -Filter *Controller.java | ForEach-Object {
    $text = Get-Content -Raw -Encoding UTF8 $_.FullName
    $ctrlName = $_.BaseName

    # 类级基础路径
    $classBase = ''
    if ($text -match '@RequestMapping\(\s*"([^"]*)"') { $classBase = $matches[1] }

    # 逐行扫描：记住最近的权限/审计注解，遇到 Mapping 就落一条记录
    $lastPerm = ''
    $lastAudit = ''
    $inRecord = $false
    foreach ($line in ($text -split "`n")) {
        $t = $line.Trim()
        if ($t -match '^public record ') { $inRecord = $true }
        if ($inRecord -and $t -match '^\}') { $inRecord = $false; continue }
        if ($inRecord) { continue }

        if ($t -match '@SaCheckPermission\(\s*(?:value\s*=\s*)?"([^"]+)"') { $lastPerm = $matches[1]; continue }
        if ($t -match '^@SaCheckPermission') { $lastPerm = '(注解存在，未解析到码)'; continue }
        if ($t -match '@AuditLog\(.*action\s*=\s*"([^"]+)"') { $lastAudit = $matches[1]; continue }
        if ($t -match '@AuditLog\(') { $lastAudit = '(已标注)'; continue }

        if ($t -match '^@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?(?:path\s*=\s*)?"([^"]*)"') {
            $verb = $matches[1].ToUpper()
            $sub  = $matches[2]
            $full = ($classBase.TrimEnd('/') + '/' + $sub.TrimStart('/')).Replace('//', '/')
            if ($full -eq '') { $full = '/' }
            $key = "$verb $full"
            $map[$key] = [pscustomobject]@{ Perm = $lastPerm; Audit = $lastAudit; Ctrl = $ctrlName }
            $lastPerm = ''; $lastAudit = ''
            continue
        }
        if ($t -match '^@(Get|Post|Put|Delete|Patch)Mapping\s*$') {
            $verb = $matches[1].ToUpper()
            $full = $classBase
            $key = "$verb $full"
            $map[$key] = [pscustomobject]@{ Perm = $lastPerm; Audit = $lastAudit; Ctrl = $ctrlName }
            $lastPerm = ''; $lastAudit = ''
            continue
        }
    }
}

# ---------- 3. 公开路径（与 WebConfig.PUBLIC_PATHS 对齐） ----------
$webConfig = Get-Content -Raw -Encoding UTF8 (Join-Path $SrcRoot 'common\config\WebConfig.java')
$publicPaths = @()
if ($webConfig -match '(?s)PUBLIC_PATHS\s*=\s*\{(.*?)\};') {
    $publicPaths = [regex]::Matches($matches[1], '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
}

function Is-Public($path) {
    # 拦截器只 match /api/**，非 /api 开头的路径（如 /health）不经过鉴权链
    if (-not $path.StartsWith('/api')) { return $true }
    foreach ($pp in $publicPaths) {
        $rx = '^' + ($pp -replace '\{[^}]+\}', '[^/]+' -replace '\*\*', '.*' -replace '\*', '[^/]*') + '$'
        if ($path -match $rx) { return $true }
    }
    return $false
}

function Param-Text($op) {
    $parts = @()
    foreach ($pr in $op.parameters) {
        if (-not $pr) { continue }
        $need = if ($pr.required) { '必填' } else { '可选' }
        if ($pr.in -eq 'query') { $parts += "$($pr.name)（query，$need）" }
        elseif ($pr.in -eq 'path') { $parts += "$($pr.name)（path）" }
        elseif ($pr.in -eq 'header') { $parts += "$($pr.name)（header，$need）" }
        else { $parts += "$($pr.name)（$($pr.in)，$need）" }
    }
    if ($op.requestBody) { $parts += 'body(JSON)' }
    return ($parts -join '、')
}

# ---------- 4. 组织输出 ----------
$rows = @()
foreach ($p in ($paths.Keys | Sort-Object)) {
    foreach ($m in ($paths[$p].PSObject.Properties.Name)) {
        $verb = $m.ToUpper()
        if ($verb -notin @('GET','POST','PUT','DELETE','PATCH')) { continue }
        $op = $paths[$p].$m
        $key = "$verb $p"
        $info = $map[$key]

        $summary = if ($op.summary) { $op.summary } else { '' }
        $perm  = if ($info -and $info.Perm) { $info.Perm } elseif (Is-Public $p) { '公开' } else { '仅要求登录' }
        $audit = if ($info -and $info.Audit) { $info.Audit } else { '' }
        $ctrl  = if ($info) { $info.Ctrl } else { '' }
        $paramText = Param-Text $op

        $rows += [pscustomobject]@{
            Method = $verb; Path = $p; Ctrl = $ctrl; Auth = $perm; Audit = $audit; Param = $paramText; Tag = $summary
        }
    }
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('# 接口文档（当前实现）')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('> 本文由 `tools/gen-api-doc.ps1` 从运行中的后端 OpenAPI（`/v3/api-docs`）与源码注解自动生成，反映**代码真实行为**，与《考研AI导学系统接口设计文档 总册 V1.0》并列作为实现态口径。')
[void]$sb.AppendLine('> 生成时间：' + (Get-Date -Format 'yyyy-MM-dd HH:mm') + '　接口总数：' + $rows.Count)
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 一、鉴权三层模型')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('```')
[void]$sb.AppendLine('SaInterceptor（WebConfig）：/api/** 除 PUBLIC_PATHS 外 → StpUtil.checkLogin()  → AccountStatusGuard.ensureActive()（status=0 → 403+10013）')
[void]$sb.AppendLine('@SaCheckPermission    ：方法级权限码 → StpInterfaceImpl → RbacSnapshotCache（禁用的角色不参与鉴权）')
[void]$sb.AppendLine('DataScopeService      ：数据归属/数据范围（SELF / ALL / MASKED）')
[void]$sb.AppendLine('```')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 二、公开接口（无需 Token）')
[void]$sb.AppendLine('')
foreach ($pp in $publicPaths) { [void]$sb.AppendLine("- ``$pp``") }
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 三、接口清单')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| 方法 | 路径 | 权限码 | 审计动作 | 主要参数 | 控制器 |')
[void]$sb.AppendLine('|---|---|---|---|---|---|')
foreach ($r in $rows) {
    $auth = if ($r.Auth -eq '公开') { '**公开**' } else { $r.Auth }
    [void]$sb.AppendLine("| $($r.Method) | ``$($r.Path)`` | $auth | $($r.Audit) | $($r.Param) | $($r.Ctrl) |")
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 四、统一响应与错误码')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('```json')
[void]$sb.AppendLine('{ "code": 0, "message": "success", "data": ... }')
[void]$sb.AppendLine('```')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| HTTP | code | 含义 | 前端动作 |')
[void]$sb.AppendLine('|---|---|---|---|')
[void]$sb.AppendLine('| 401 | 10001 | 未登录 / Token 失效 | 清 Token，跳登录页 |')
[void]$sb.AppendLine('| 401 | 10002 | 账号或密码错误 | 表单内提示 |')
[void]$sb.AppendLine('| 400 | 10005 | 验证码错误或过期 | 刷新图形码 |')
[void]$sb.AppendLine('| 409 | 10004 | 账号已存在 | 提示换一个 |')
[void]$sb.AppendLine('| 423 | 10003 | 账号锁定 | 提示解除时间 |')
[void]$sb.AppendLine('| 429 | 10010 | 触发限流 | 按 `Retry-After` 倒计时 |')
[void]$sb.AppendLine('| 403 | 10008 | 无接口权限 | 提示无权限，**不跳登录** |')
[void]$sb.AppendLine('| 403 | 10009 | 无权访问该数据 | 提示无权访问 |')
[void]$sb.AppendLine('| 403 | 10013 | 账号已禁用 | 清 Token，提示联系管理员 |')
[void]$sb.AppendLine('| 400 | 10000 | 参数校验失败 | 按字段提示 |')
[void]$sb.AppendLine('| 500 | 90000 | 服务器异常 | 通用错误页，附 `X-Request-Id` |')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 五、与设计文档的覆盖关系')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('> **基准要选对**：全系统口径看《总册 V1.0》（55 条业务路径，含题库/错题/模考/院校/新闻，多数属其他模块任务）；登录授权模块口径看《V1.1 适配版》（85 条路径，其中第 13.2 节「用户与 RBAC」登记 9 条）。下表按 V1.1 适配版 13.2 节核对。')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('### 5.1 第 13.2 节「用户与 RBAC」——9 条全部实现')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('用户列表、用户详情、启用禁用、用户角色（查/改）、角色列表、新增角色、权限列表、分配角色权限：功能全部具备。')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('### 5.2 实现侧比文档多出的 7 条（文档未收录）')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| 接口 | 用途 | 处理建议 |')
[void]$sb.AppendLine('|---|---|---|')
[void]$sb.AppendLine('| `PUT /api/v1/admin/roles/{id}` | 角色编辑 | 补进文档 13.2 |')
[void]$sb.AppendLine('| `DELETE /api/v1/admin/roles/{id}` | 角色删除 | 补进文档 13.2 |')
[void]$sb.AppendLine('| `GET /api/v1/admin/roles/{id}/permissions` | 查角色权限 | 补进文档 13.2 |')
[void]$sb.AppendLine('| `POST /api/v1/admin/users/{userId}/roles/{roleId}` | 绑定单个角色 | 补进文档 13.2 |')
[void]$sb.AppendLine('| `DELETE /api/v1/admin/users/{userId}/roles/{roleId}` | 解绑单个角色 | 补进文档 13.2 |')
[void]$sb.AppendLine('| `GET /api/v1/admin/login-logs` | 登录日志查询 | 补进文档 13.2 |')
[void]$sb.AppendLine('| `POST /api/v1/admin/vector/reindex/{kbId}` | 向量索引重建 | 对应文档 11.1 的 `POST /api/v1/admin/knowledge-bases/{kb_id}/vectors/rebuild` |')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('### 5.3 路径前缀：已与文档对齐')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| 项 | 文档口径（V1.1） | 当前实现 | 状态 |')
[void]$sb.AppendLine('|---|---|---|---|')
[void]$sb.AppendLine('| 后台前缀 | `/api/v1/admin/**` | `/api/v1/admin/**` | ✅ 已对齐（P2 改造清单 D 项已完成） |')
[void]$sb.AppendLine('| 聊天前缀 | `/api/v1/chat/**` | `/api/v1/chat/**` | ✅ 已对齐；流式问答按文档挂在 `/conversations/{id}/messages/stream`，旧 `/stream` 保留为兼容别名 |')
[void]$sb.AppendLine('| 知识库 | `/api/v1/knowledge-bases/**`、`/api/v1/knowledge-documents/**` | 同文档 | ✅ 已对齐（原 `/api/kb`、`/api/doc` 已废弃） |')
[void]$sb.AppendLine('| 用户 | `/api/v1/user/profile`、`/api/v1/user/study-goals` | 同文档 | ✅ 已对齐（原 `/api/user/**`、`/api/v1/user/goals` 已迁移） |')
[void]$sb.AppendLine('| 文件 | `/api/v1/files/{file_id}` | `/api/v1/files/{fileId}` | 仅路径参数变量名不同，URL 形状一致 |')
[void]$sb.AppendLine('| 认证 | `/api/v1/auth/**` | `/api/v1/auth/**` | ✅ 完全一致 |')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('### 5.3.1 仍存在的语义差异（文档形态 vs 实现形态）')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| 项 | 文档 | 实现 | 处理 |')
[void]$sb.AppendLine('|---|---|---|---|')
[void]$sb.AppendLine('| 新建会话 | `POST /api/v1/chat/conversations` 显式建会话 | 首次流式问答（`conversation_id` 为空）隐式建会话 | 功能等价，路径暂未单独开；如前端需要显式建会话再补接口 |')
[void]$sb.AppendLine('| 备考目标写入 | `PUT/PATCH /api/v1/user/study-goals`（集合式全量/局部更新，PATCH 带 Idempotency-Key） | `POST`（新增）、`PUT /{id}`、`DELETE /{id}` | 路径已对齐到 `study-goals`；集合式 PUT/PATCH 属新增能力，列入遗留项 |')
[void]$sb.AppendLine('| 文档解析内容 | `GET /api/v1/knowledge-documents/{document_id}/content` | 未实现 | 列入遗留项（解析文本读取接口） |')
[void]$sb.AppendLine('| 改密 | 文档 3.6 `POST /api/v1/auth/password-reset`（未登录重置） | 另有 `PUT /api/v1/user/password`（已登录改密，文档未登记） | 实现侧扩展，保留并在此登记 |')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('### 5.4 文档已定义但尚未实现的模块（不在 P2 范围）')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('题库 `questions`、错题本 `wrong-questions`、答题记录 `answer-records`、模考 `mock-exams`、掌握度 `mastery`、院校 `schools`、学习计划 `study-plans`、资讯 `news`、拍照搜题 `image-question-tasks`、平台统计 `admin/stats` —— 属题库/院校/督学等模块负责人的任务。')
[void]$sb.AppendLine('')

[IO.File]::WriteAllText($OutFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($true)))
Write-Host "已生成：$OutFile（$($rows.Count) 个接口）"

# 同时导出 OpenAPI JSON，便于前端用工具生成类型
$jsonOut = Join-Path $RepoRoot 'docs\00_项目管理\openapi-后端当前实现.json'
[IO.File]::WriteAllText($jsonOut, (ConvertTo-Json $spec -Depth 32), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "已导出：$jsonOut"
