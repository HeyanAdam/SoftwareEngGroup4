# C 完成后的认证联调与文件存储最终调试指南

> 读者：集成负责人 D、前端认证负责人 C、文件模块负责人 A 和 B
> 触发条件：C 已交付登录页面、共享 Token 会话层和请求处理
> 目标：从开发阶段临时 Token 切换到正式登录态，并完成文件存储的端到端验收

## 1. 正式认证的目标状态

最终调用链：

```text
登录页面
  ↓
authSession 保存 Token
  ↓
shared/api/request.ts 自动附加 Authorization
  ↓
后端 Sa-Token 解析当前用户
  ↓
文件接口按 owner_user_id 隔离数据
```

开发阶段使用的 `file.http.ts` 在集成分支中完成替换，文件组件沿用 B 的上传状态机，D 的页面布局保持不变。

## 2. C 的交付物检查

### 2.1 代码交付

C 提供：

```text
frontend/src/shared/auth/authSession.ts
frontend/src/shared/api/request.ts
frontend/src/features/auth/
frontend/src/app/router/index.ts
```

### 2.2 文档交付

C 提供认证交接文档，内容包括：

```text
最终登录接口
请求字段
响应字段
Token 存储键
当前用户接口
401 处理
403 处理
权限判断
测试账号创建方式
Token 获取方式
```

文件名可以是：

```text
docs/auth-handoff.md
```

也可以使用认证模块现有的交接说明文件，内容应覆盖上述项目。

### 2.3 行为验收

| 检查项 | 验收动作 | 通过标准 |
|---|---|---|
| Token 保存 | 登录后检查 localStorage | Token 存在 |
| Token 读取 | 请求文件接口 | Network 包含 Bearer |
| 当前用户 | 刷新页面 | `/me` 返回当前用户 |
| 401 | 清理 Token 后请求 | 自动进入登录页 |
| 403 | 无权限账号请求 | 保持登录并提示 |
| 退出 | 点击退出 | Token 清理并返回登录页 |
| 权限 | 检查 `/me` | 包含 `file:*` 权限 |
| 路由守卫 | 未登录访问 `/files` | 跳转登录页 |

## 3. GitHub 分支和合并规划

### 3.1 认证与文件集成分支

建议分支：

```text
main
└── integration/file-storage-final
    ├── auth foundation merge
    ├── feat/file-backend-storage
    ├── feat/file-upload-engine
    └── feat/file-ui-integration
```

如果团队继续使用 `integration/file-storage`，最终认证切换在同一个分支完成。

### 3.2 创建 GitHub Issue

创建：

```text
[FILE-INTEGRATION] 正式认证接入与文件存储端到端验收
```

Issue 检查项：

```text
认证分支已合并
临时 Token 已移除
file.api.ts 已使用共享 request
登录、401、403、退出已验收
小文件、大文件、续传、下载已验收
双账号归属已验收
截图和视频已归档
```

### 3.3 创建最终集成分支

```powershell
git fetch origin
git switch main
git pull --ff-only
git switch -c integration/file-storage-final
git push -u origin integration/file-storage-final
```

### 3.4 合并 Pull Request

建议合并顺序：

1. 认证后端和认证前端基础。
2. A 的文件后端。
3. B 的上传引擎。
4. D 的文件页面和业务接入。
5. 临时 Token 替换提交。

每个 Pull Request 目标分支：

```text
integration/file-storage-final
```

最终 Pull Request：

```text
integration/file-storage-final -> main
```

### 3.5 GitHub 评审分工

| Pull Request | 主要评审人 |
|---|---|
| 认证基础 | A、D |
| A 后端 | B、C、D |
| B 上传引擎 | A、D |
| D 页面 | A、B、C |
| 最终认证切换 | A、B、C |

## 4. 仓库结构检查

认证分支合并前检查根目录：

```text
backend/
frontend/
deploy/
docs/
README.md
```

仓库使用一套根级工程目录。认证分支如果带有嵌套的完整仓库副本，在集成分支中提交一次清理提交，将代码统一保留在根级目录。

## 5. 合并认证分支

确认权威认证分支后执行：

```powershell
git switch integration/file-storage-final
git pull --ff-only
git merge origin/feat/auth-foundation
```

冲突文件按 owner 处理：

| 文件 | Owner |
|---|---|
| `modules/file/**` | A |
| `platform/storage/**` | A |
| `uploader.ts`、`file.api.ts` | B |
| `authSession.ts`、登录页 | C |
| `request.ts` | C |
| `FilePage.vue`、`MainLayout.vue` | D |

每次解决冲突后执行：

```powershell
git status
git diff --check
```

## 6. 确认认证接口契约

启动后端并打开 Swagger：

```text
http://localhost:8080/swagger-ui/index.html
```

记录最终接口：

```text
登录
当前用户
退出
验证码
注册
```

新认证规范采用：

```text
POST /api/v1/auth/login
GET  /api/v1/auth/me
POST /api/v1/auth/logout
```

文件接口保持：

```text
/api/v1/files
```

将最终路径和字段写入认证交接文档。

## 7. 构建检查

后端：

```powershell
cd backend
mvn test
```

前端：

```powershell
cd frontend
npm install
npm run build
```

构建通过后进入浏览器联调。

## 8. 启动完整环境

基础服务：

```powershell
cd deploy
docker compose up -d mysql redis rabbitmq minio
docker compose ps
```

后端：

```powershell
cd backend
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm run dev
```

访问：

```text
http://localhost:5173
```

## 9. 创建两个测试账号

账号用途：

```text
file_owner_a：上传和拥有文件
file_owner_b：验证无法访问 A 的文件
```

创建方式按最终认证接口执行：

1. 打开登录或注册页面。
2. 使用测试邮箱。
3. 从 Mailpit 获取验证码：

```text
http://localhost:8025
```

4. 完成账号 A 注册。
5. 完成账号 B 注册。
6. 记录两个账号的用户名和密码到团队内部凭据渠道。
7. GitHub Issue 中记录账号用途和测试结果。

## 10. 切换文件 API 到共享请求

### 10.1 检查共享客户端

`frontend/src/shared/api/request.ts` 应包含：

- `/api/v1` baseURL。
- Token 请求拦截。
- `Authorization: Bearer <token>`。
- 401 清登录态。
- 403 保留登录态。
- 响应返回 `{ code, message, data }`。

### 10.2 修改文件 API

将：

```ts
import { fileClient } from './file.http'
```

改为：

```ts
import { request } from '@/shared/api/request'
```

文件接口路径保持：

```text
/files
/files/upload
/files/upload-sessions
```

### 10.3 删除临时认证模块

确认 `file.api.ts` 已使用共享 `request` 后，从集成提交中删除：

```text
frontend/src/features/file/file.http.ts
```

执行全仓搜索：

```powershell
rg -n "fileClient|file.http|kaoyan_access_token|localStorage" frontend/src
```

最终结果中，Token 读写集中在：

```text
frontend/src/shared/auth/
frontend/src/shared/api/request.ts
```

## 11. 调试登录态

### 11.1 登录

1. 打开前端登录页。
2. 输入账号 A。
3. 完成图形验证码或邮箱验证码。
4. 登录成功。
5. 打开浏览器 DevTools。
6. 检查 Network 中登录响应。
7. 确认 `data.token` 存在。
8. 检查 localStorage 中的 Token。

### 11.2 当前用户

Network 中检查：

```http
GET /api/v1/auth/me
```

确认响应包含：

```text
user_id
roles
permissions
has_profile
```

文件按钮按 `permissions` 中的：

```text
file:view
file:upload
file:download
file:delete
```

控制显示。

### 11.3 文件列表

打开 `/files`，检查：

```http
GET /api/v1/files
Authorization: Bearer <token>
```

请求成功返回 200 后，继续测试上传。

## 12. 调试 401

触发方式：

1. 登录账号 A。
2. 清理 localStorage 中的 Token。
3. 保持 `/files` 页面。
4. 刷新或调用列表接口。
5. 后端返回 401。
6. `request.ts` 清理登录态。
7. 路由守卫跳转登录页。
8. 登录页显示“登录已失效”。

Network 中确认：

```text
请求头没有 Authorization，或 Token 已过期
响应状态为 401
```

## 13. 调试 403

使用无文件权限的测试角色请求：

```http
GET /api/v1/files
```

检查：

1. HTTP 状态为 403。
2. 页面提示无权限。
3. Token 保留。
4. 用户可以继续访问其他有权限页面。

权限码缺失时，检查：

- `/me` 的 permissions。
- C 的权限判断方法。
- A 的接口权限注解。
- 数据库角色权限关系。

## 14. 调试小文件上传

1. 登录账号 A。
2. 进入 `/files`。
3. 上传 1MB PDF。
4. Network 检查 `POST /api/v1/files`。
5. 确认请求包含 Bearer。
6. 确认响应包含 `fileId`。
7. 文件列表刷新后出现新文件。
8. MinIO 中出现正式对象。
9. MySQL 中出现 `file_object`。

## 15. 调试大文件和续传

### 15.1 初始化

上传 60MB 以上文件，检查：

```http
POST /api/v1/files/upload-sessions
```

响应记录：

```text
sessionId
partSize
partCount
expiresAt
```

### 15.2 分片

检查：

```http
PUT /api/v1/files/upload-sessions/{sessionId}/parts/{partNumber}
```

每个请求包含：

```http
X-Part-Hash: <SHA-256>
Authorization: Bearer <token>
```

### 15.3 暂停

上传到约一半时点击暂停。

查询：

```http
GET /api/v1/files/upload-sessions/{sessionId}
```

响应应包含：

```json
{
  "uploadedParts": [1, 2, 3],
  "missingParts": [4, 5, 6]
}
```

### 15.4 刷新

1. 刷新浏览器。
2. 重新选择同一文件。
3. 文件指纹匹配原会话。
4. Network 中只上传 `missingParts`。
5. 已完成分片保持原记录。

### 15.5 合并

检查：

```http
POST /api/v1/files/upload-sessions/{sessionId}/complete
```

成功后：

- 响应返回 `FileObject`。
- `file_object.status = READY`。
- 上传会话状态为 `COMPLETED`。
- 正式对象存在。
- 临时分片完成清理。

## 16. 调试 Range 和 ETag

PowerShell：

```powershell
$headers = @{
  Authorization = "Bearer 你的Token"
  Range = "bytes=0-99"
}

$response = Invoke-WebRequest `
  -Uri "http://localhost:8080/api/v1/files/文件ID/download" `
  -Headers $headers `
  -SkipHttpErrorCheck

$response.StatusCode
$response.Headers["Accept-Ranges"]
$response.Headers["Content-Range"]
$response.Headers["ETag"]
```

预期：

```text
状态码：206
Accept-Ranges：bytes
Content-Range：bytes 0-99/总大小
ETag：文件 SHA-256
```

### 16.1 非法 Range

```powershell
$headers = @{
  Authorization = "Bearer 你的Token"
  Range = "bytes=999999999-1000000000"
}
```

预期：

```text
416
Content-Range: bytes */文件大小
```

### 16.2 ETag 不匹配

```http
Range: bytes=100-199
If-Range: "错误ETag"
```

预期返回完整 `200`。

### 16.3 ETag 匹配

```http
Range: bytes=100-199
If-Range: "<正确ETag>"
```

预期返回 `206`。

## 17. 调试双账号归属

### 17.1 账号 A

1. 登录账号 A。
2. 上传文件。
3. 记录 `fileId`。
4. 确认列表可见。
5. 确认下载成功。

### 17.2 账号 B

1. 退出账号 A。
2. 登录账号 B。
3. 打开文件列表。
4. 确认列表没有 A 的文件。
5. 使用 B 的 Token 请求 A 的 `fileId`。
6. 确认返回 403 或项目约定的归属错误。
7. 确认响应体不包含文件流。

### 17.3 恢复账号 A

1. 重新登录账号 A。
2. 确认原文件仍存在。
3. 确认可以正常下载。

## 18. 调试伪造扩展名

1. 准备文本文件或可执行文件。
2. 重命名为 `测试资料.pdf`。
3. 使用账号 A 上传。
4. 确认 HTTP 400。
5. 查看后端 message。
6. 检查 MinIO，确认没有该上传产生的新对象。
7. 在 Issue 中记录测试结果。

## 19. 调试清理

### 19.1 取消会话

1. 上传若干分片。
2. 点击取消。
3. 检查会话状态为 `CANCELLED`。
4. 检查 `file_upload_part` 记录清空。
5. 检查 MinIO 临时对象删除。

### 19.2 过期会话

1. 将测试会话 `expires_at` 改为过去时间。
2. 等待清理任务。
3. 检查会话状态。
4. 检查分片记录。
5. 检查对象存储。

## 20. GitHub 最终验收

在最终 Pull Request 中填写：

```text
后端测试结果
前端构建结果
小文件截图
批量上传截图
大文件续传截图
Range 206 截图
双账号归属截图
伪造扩展名截图
演示视频链接
已知后续优化
```

检查 GitHub Actions：

```text
backend-ci
frontend-ci
compose-check
```

三个工作流通过后，请求 D、A、B、C 完成最终 Review。

## 21. 发布前检查

- [ ] 登录页面可用。
- [ ] Token 自动附带。
- [ ] 当前用户可恢复。
- [ ] `file.http.ts` 已删除。
- [ ] 文件 API 使用共享 `request`。
- [ ] 401 返回登录页。
- [ ] 403 显示无权限。
- [ ] 文件列表按用户隔离。
- [ ] 小文件上传成功。
- [ ] 批量上传成功。
- [ ] 大文件续传成功。
- [ ] Range 返回 206。
- [ ] 非法 Range 返回 416。
- [ ] ETag 恢复校验成功。
- [ ] 伪造扩展名返回 400。
- [ ] 过期会话清理成功。
- [ ] 两个账号归属测试通过。
- [ ] 截图和视频已归档。
- [ ] GitHub Actions 通过。

## 22. 完成定义

以下条件全部成立时，文件存储功能完成：

- 用户通过正式登录页面进入系统。
- 文件模块不依赖手工注入 Token。
- 登录态失效时统一回到登录页。
- 文件归属来自后端登录态。
- 小文件、批量、大文件、续传、下载和删除全部可用。
- 用户 A 和用户 B 的数据严格隔离。
- Range、ETag 和非法范围响应符合接口约定。
- 伪造扩展名被类型校验拒绝。
- 集成分支通过全部 CI 和验收。
- 最终 Pull Request 已合入 `main`。
