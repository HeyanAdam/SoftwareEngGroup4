<#
权限菜单验收脚本（对应任务A 验收表 AUTH-1 ~ AUTH-6 中可用接口验证的部分）

做的事：
  1. 管理员登录，读出 STUDENT 角色的原始权限（用作恢复快照）
  2. 从 STUDENT 角色移除 study-plan:view / study-plan:create（模拟“管理员取消学生权限”）
  3. 学生重新登录 → /auth/me 的权限里不再有 study-plan:*（菜单 v-if 的数据源）
  4. 学生访问管理员接口 → 403（后端 @SaCheckPermission 仍在，不靠前端隐藏）
  5. 恢复 STUDENT 原始权限
  6. 学生重新登录 → study-plan:* 回来（AUTH-3）

说明：菜单是否渲染由前端 MainLayout 的 v-if 决定，脚本只能验证权限数据；
      页面上的显隐请按 AUTH-1/AUTH-3 手工截图。

用法（仓库根目录，后端已启动在 8080）：
  pwsh tools\permission-acceptance.ps1
  pwsh tools\permission-acceptance.ps1 -StudentAccount student1 -StudentPassword 'Student@123456'
#>
param(
    [string]$Base = 'http://localhost:8080',
    [string]$AdminAccount = 'admin',
    [string]$AdminPassword = 'Admin@123456',
    [string]$StudentAccount = 'student1',
    [string]$StudentPassword = 'Student@123456',
    [string]$RedisContainer = 'kaoyan-redis',
    [long]$StudentRoleId = 2
)

$ErrorActionPreference = 'Continue'
$script:pass = 0; $script:fail = 0
$docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $docker) { $docker = 'D:\Docker\Desktop\resources\bin\docker.exe' }

function Step($name, $ok, $detail) {
    if ($ok) { $script:pass++; Write-Host ("  [OK]   " + $name + "  " + $detail) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  [FAIL] " + $name + "  " + $detail) -ForegroundColor Red }
}

function Captcha() {
    $cap = (Invoke-RestMethod "$Base/api/v1/auth/captcha").data
    $raw = & $docker exec $RedisContainer redis-cli --raw GET ("auth:captcha:" + $cap.captcha_key) 2>$null
    return [pscustomobject]@{ Key = $cap.captcha_key; Code = (($raw -join '').Trim()) }
}

function Login($account, $password) {
    $c = Captcha
    $body = @{ login_type = 'PASSWORD'; account = $account; password = $password; captcha_key = $c.Key; captcha_code = $c.Code } | ConvertTo-Json
    $r = Invoke-RestMethod "$Base/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $body
    return $r.data.token
}

function StatusOf($url, $headers) {
    try {
        Invoke-RestMethod $url -Headers $headers -TimeoutSec 30 | Out-Null
        return 200
    }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

Write-Host "`n=== 权限验收（AUTH-1/2/3/4/5/6 接口侧） ===" -ForegroundColor Cyan

$adminToken = Login $AdminAccount $AdminPassword
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
Step '管理员登录' ([bool]$adminToken) "account=$AdminAccount"

$allPerms = (Invoke-RestMethod "$Base/api/v1/admin/permissions" -Headers $adminHeaders).data
$codeToId = @{}
foreach ($p in $allPerms) { $codeToId[$p.permCode] = $p.id }

$origCodes = @((Invoke-RestMethod "$Base/api/v1/admin/roles/$StudentRoleId/permissions" -Headers $adminHeaders).data)
$origIds = @($origCodes | ForEach-Object { $codeToId[$_] } | Where-Object { $_ })
Write-Host ("  STUDENT 原始权限：" + ($origCodes -join ', ')) -ForegroundColor DarkGray

try {
    # ---- AUTH-1：移除学习计划权限 ----
    $without = @($origIds | Where-Object { $_ -ne $codeToId['study-plan:view'] -and $_ -ne $codeToId['study-plan:create'] })
    Invoke-RestMethod "$Base/api/v1/admin/roles/$StudentRoleId/permissions" -Method Put -Headers $adminHeaders `
        -ContentType 'application/json' -Body (@{ ids = $without } | ConvertTo-Json -Depth 4) | Out-Null
    $nowCodes = @((Invoke-RestMethod "$Base/api/v1/admin/roles/$StudentRoleId/permissions" -Headers $adminHeaders).data)
    Step 'AUTH-1 管理员取消学生 study-plan:view / study-plan:create' (-not ($nowCodes -contains 'study-plan:view' -or $nowCodes -contains 'study-plan:create')) ("剩余：" + ($nowCodes -join ','))

    # 学生重新登录，拿新的权限快照
    $stuToken = Login $StudentAccount $StudentPassword
    $me = (Invoke-RestMethod "$Base/api/v1/auth/me" -Headers @{ Authorization = "Bearer $stuToken" }).data
    $mePerms = @($me.permissions)
    Step 'AUTH-1 学生权限快照已同步（无 view/create，菜单不渲染）' `
        (-not ($mePerms -contains 'study-plan:view' -or $mePerms -contains 'study-plan:create')) ("permissions=" + ($mePerms -join ','))

    # ---- AUTH-2：越权访问仍被后端拦截 ----
    $adminApi = StatusOf "$Base/api/v1/admin/users" @{ Authorization = "Bearer $stuToken" }
    Step 'AUTH-2/5 学生访问管理员接口返回 403' ($adminApi -eq 403) "status=$adminApi"
    # ---- AUTH-4：学生没有 admin 权限，菜单里的“管理后台”不渲染 ----
    Step 'AUTH-4 学生权限中不含 admin:*（“管理后台”入口不渲染）' `
        (-not ($mePerms | Where-Object { $_ -like 'admin:*' })) ("admin 权限数=" + (@($mePerms | Where-Object { $_ -like 'admin:*' }).Count))
    # 学生正常业务不受影响
    $chatApi = StatusOf "$Base/api/v1/chat/conversations" @{ Authorization = "Bearer $stuToken" }
    Step '学生正常业务接口仍可用（chat 会话列表 200）' ($chatApi -eq 200) "status=$chatApi"

    # ---- AUTH-3：恢复权限 ----
    Invoke-RestMethod "$Base/api/v1/admin/roles/$StudentRoleId/permissions" -Method Put -Headers $adminHeaders `
        -ContentType 'application/json' -Body (@{ ids = $origIds } | ConvertTo-Json -Depth 4) | Out-Null
    Start-Sleep -Seconds 1
    $restored = @((Invoke-RestMethod "$Base/api/v1/admin/roles/$StudentRoleId/permissions" -Headers $adminHeaders).data)
    Step 'AUTH-3 恢复 study-plan 权限' ([bool]($restored | Where-Object { $_ -eq 'study-plan:view' })) ("恢复后：" + ($restored -join ','))

    $stuToken2 = Login $StudentAccount $StudentPassword
    $me2 = (Invoke-RestMethod "$Base/api/v1/auth/me" -Headers @{ Authorization = "Bearer $stuToken2" }).data
    Step 'AUTH-3 学生重新登录后 study-plan:view 回来（菜单重新出现）' `
        (@($me2.permissions) -contains 'study-plan:view') ("permissions=" + (@($me2.permissions) -join ','))

    # ---- AUTH-6：管理员可以看到/进入管理后台 ----
    $admPerms = @($me.permissions) # 占位，下面重新取管理员
    $admMe = (Invoke-RestMethod "$Base/api/v1/auth/me" -Headers $adminHeaders).data
    Step 'AUTH-6 管理员具备 admin:user:list（“管理后台”入口可见）' `
        ([bool](@($admMe.permissions) | Where-Object { $_ -eq 'admin:user:list' })) ("roles=" + (@($admMe.roles) -join '/'))
}
finally {
    # 兜底恢复，避免脚本异常中断后角色权限停在“已移除”状态
    try {
        Invoke-RestMethod "$Base/api/v1/admin/roles/$StudentRoleId/permissions" -Method Put -Headers $adminHeaders `
            -ContentType 'application/json' -Body (@{ ids = $origIds } | ConvertTo-Json -Depth 4) | Out-Null
        Write-Host '  (finally) 已恢复 STUDENT 角色原始权限' -ForegroundColor DarkGray
    }
    catch {
        Write-Host '  (finally) 恢复失败，请在后台【角色权限】页手动恢复 study-plan 权限' -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host ("通过 $($script:pass) 项，失败 $($script:fail) 项") -ForegroundColor ($(if ($script:fail -eq 0) { 'Green' } else { 'Yellow' }))
Write-Host '提示：菜单显隐属于前端渲染，请按 AUTH-1/AUTH-3 保存页面截图作为验收证据。' -ForegroundColor DarkGray
exit ([int]($script:fail -gt 0))
