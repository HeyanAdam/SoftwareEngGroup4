# 人员 D：文件页面接入与验收操作指南

> 读者：文件功能集成负责人 D、产品和验收人员
> 目标：完成文件管理页面、业务入口、端到端联调和最终验收
> 工作方式：D 维护集成分支，统一接收 A、B、C 的 Pull Request

## 1. D 的工作职责

D 负责让文件存储成为可用产品功能，交付：

- 文件管理页面。
- 文件列表、筛选、分页和搜索。
- 上传入口和全局上传任务中心。
- 下载、删除和失败重试。
- 聊天附件入口。
- 学习资料上传入口。
- 两个账号的归属测试。
- Range 下载验收。
- 截图、演示视频和验收记录。
- 集成分支维护和最终合并。

### 1.1 消费 A 和 B 的成果

D 接收 A 的：

```text
文件列表接口
文件详情接口
下载接口
删除接口
错误码
测试账号下的真实数据
```

D 接收 B 的：

```text
file.api.ts
UploadTaskCenter.vue
任务事件
上传状态
续传能力
```

D 接收 C 的：

```text
登录页面
authSession
共享 request
401/403 处理
路由守卫
权限判断
```

### 1.2 文件维护责任

D 维护：

```text
frontend/src/features/file/FilePage.vue
frontend/src/features/file/components/FileTable.vue
frontend/src/app/layouts/MainLayout.vue
frontend/src/features/chat/ChatPage.vue
frontend/src/features/studyplan/StudyPlanPage.vue
docs/file-storage/evidence/
```

D 负责创建和维护集成分支。各模块文件由对应 owner 维护，变更通过 Pull Request Review 完成。

## 2. GitHub 分支规划

### 2.1 创建集成分支

D 在 GitHub 上创建 Issue：

```text
[FILE-D] 文件页面、业务接入与最终验收
```

从最新主线创建集成分支：

```powershell
git fetch origin
git switch main
git pull --ff-only
git switch -c integration/file-storage
git push -u origin integration/file-storage
```

### 2.2 GitHub 分支结构

```text
main
└── integration/file-storage
    ├── feat/file-backend-storage       A
    ├── feat/auth-frontend-session      C
    ├── feat/file-upload-engine         B
    └── feat/file-ui-integration        D
```

### 2.3 GitHub Project 看板

建立以下列：

```text
Backlog
Ready
In Progress
Review
Integration
Verified
Done
```

每个 Issue 对应一个模块，Card 移动到 `Integration` 时表示 Pull Request 已进入集成分支。

### 2.4 Pull Request 目标分支

所有开发 Pull Request 的目标分支：

```text
integration/file-storage
```

功能全部验收后，D 创建最终 Pull Request：

```text
integration/file-storage -> main
```

### 2.5 合并顺序

建议顺序：

1. A 的后端存储。
2. C 的认证前端。
3. B 的上传引擎。
4. D 的页面和业务接入。

每次合并后立即执行：

```powershell
git switch integration/file-storage
git pull --ff-only
git log --oneline -10
git status --short
```

## 3. 创建 D 的功能分支

从集成分支创建：

```powershell
git switch integration/file-storage
git pull --ff-only
git switch -c feat/file-ui-integration
git push -u origin feat/file-ui-integration
```

D 的提交拆分：

```text
feat(file): 完成文件列表和分页
feat(file): 接入上传任务中心
feat(file): 增加下载删除和重试操作
feat(file): 接入聊天附件入口
feat(file): 接入学习资料上传入口
test(file): 增加双账号文件归属验收
docs(file): 补充验收截图和演示说明
```

## 4. 文件管理入口

项目已有 `/files` 路由。D 按产品需要选择一种入口方案：

### 方案 A：菜单入口

在主导航中保留：

```text
我的文件
```

点击进入 `/files`。

### 方案 B：用户菜单入口

在用户菜单中增加：

```text
我的文件
```

打开 `/files` 页面或文件管理抽屉。

### 方案 C：业务内入口

聊天提供附件按钮，学习计划提供上传资料按钮，文件管理统一通过用户菜单进入。

选择方案后，在产品说明中记录入口位置和操作路径。

## 5. 完成文件管理页面

页面结构：

```text
页面标题
上传按钮
场景筛选
文件名搜索
文件表格
分页
上传任务中心
```

表格列：

```text
文件名
类型
大小
场景
状态
上传时间
操作
```

操作按钮：

```text
下载
删除
重试
```

### 5.1 场景显示

| 枚举 | 显示名称 |
|---|---|
| `STUDY_MATERIAL` | 学习资料 |
| `CHAT_ATTACHMENT` | 聊天附件 |
| `QUESTION_IMAGE` | 题目图片 |
| `KNOWLEDGE_DOCUMENT` | 知识文档 |
| `AVATAR` | 头像 |

### 5.2 列表查询

```http
GET /api/v1/files?scene=STUDY_MATERIAL&page=1&size=10
```

页面状态：

```text
loading
files
total
page
size
scene
keyword
```

后端暂未提供关键字搜索时，D 在当前页数据上执行前端过滤，并在接口说明中记录服务端搜索作为后续优化。

## 6. 上传任务组件接入

D 使用：

```vue
<UploadTaskCenter
  :files="selectedFiles"
  :scene="scene"
  :auto-start="true"
  @completed="handleUploadCompleted"
  @failed="handleUploadFailed"
/>
```

上传完成后的动作：

- 刷新文件列表。
- 更新当前页面选中附件。
- 在聊天中生成附件卡片。
- 在学习计划中保存 `fileId`。
- 显示成功消息。

上传失败后的动作：

- 展示任务中心中的错误。
- 保留失败任务。
- 用户点击重试时由 B 的组件继续处理。

## 7. 下载和删除

### 7.1 普通下载

使用 A 的下载接口：

```http
GET /api/v1/files/{fileId}/download
Authorization: Bearer <token>
```

页面流程：

1. 用户点击下载。
2. 页面显示加载状态。
3. 请求下载接口。
4. 接收文件流或预签名地址。
5. 保存文件。
6. 显示成功提示。

需要 Range 或断点恢复时，使用同一后端下载接口和 `Range` 请求头。

### 7.2 删除

```http
DELETE /api/v1/files/{fileId}
```

页面流程：

1. 用户点击删除。
2. 弹出确认框。
3. 调用删除接口。
4. 刷新列表。
5. 当前页为空且页码大于 1 时回到上一页。

## 8. 全局上传任务中心

D 将任务中心挂载到主布局右下角。

交互要求：

- 页面切换后任务继续。
- 关闭面板后任务继续。
- 完成和失败有提示。
- 暂停、继续、取消按钮调用 B 提供的方法。
- 移动端调整宽度和位置。

## 9. 聊天附件接入

在聊天输入框附近增加附件按钮。

流程：

1. 用户选择文件。
2. D 设置场景 `CHAT_ATTACHMENT`。
3. 调用 B 的上传任务。
4. 上传完成后保存返回的 `fileId`。
5. 发送消息时携带附件元数据。
6. 消息区域显示附件卡片。
7. 用户点击卡片时调用文件详情或下载接口。

聊天后端暂未支持附件字段时，先把附件保存到待发送列表，并在消息接口完成后补充持久化关联。

## 10. 学习计划资料接入

在学习计划页增加“上传资料”按钮。

流程：

1. 选择文件。
2. 设置场景 `STUDY_MATERIAL`。
3. 上传完成后取得 `fileId`。
4. 刷新学习资料列表。
5. 学习计划持久化接口完成后保存计划与文件关联。

## 11. C 完成前的开发方式

C 的页面尚未完成时，D 使用真实 Token 进行页面联调。

从后端取得 Token 后，在浏览器控制台执行：

```js
localStorage.setItem('kaoyan_access_token', '真实Token')
```

B 的临时客户端负责自动添加：

```http
Authorization: Bearer <token>
```

在 Network 中检查每个文件请求都带此请求头。

## 12. C 完成后的正式接入

C 合并后，D 在认证切换 Pull Request 中完成：

1. 使用 C 的 `authSession`。
2. 文件 API 改用共享 `request`。
3. 删除 `file.http.ts`。
4. 验证登录页可写入 Token。
5. 验证 401 自动返回登录页。
6. 验证 403 显示无权限提示。
7. 验证文件页面受路由守卫保护。

详细步骤见：

```text
docs/file-storage/C完成后认证联调与最终调试.md
```

## 13. 启动联调环境

在仓库根目录启动基础服务：

```powershell
cd deploy
docker compose up -d mysql redis rabbitmq minio
```

启动后端：

```powershell
cd backend
mvn spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:5173/files
```

## 14. 验收准备

准备：

- 账号 A。
- 账号 B。
- 1 个小 PDF。
- 5 个批量小文件。
- 1 个 60MB 以上文件。
- 1 个扩展名被修改的文件。
- A 的 Token。
- B 的 Token。
- A 上传成功的 `fileId`。

## 15. 验收矩阵

| 验收项 | 操作步骤 | 预期结果 |
|---|---|---|
| 小文件上传 | 上传 1MB PDF | 文件出现在列表 |
| 批量上传 | 一次选择 5 个文件 | 全部显示 |
| 大文件上传 | 上传 60MB 文件 | 进入分片模式 |
| 暂停 | 上传中点击暂停 | 状态变为暂停 |
| 继续 | 点击继续 | 只补缺失分片 |
| 刷新恢复 | 刷新后重新选择同一文件 | 使用原会话 |
| 普通下载 | 点击下载 | 文件内容正确 |
| Range 下载 | 发送 `bytes=0-99` | 返回 206 |
| 非法 Range | 发送超大范围 | 返回 416 |
| 401 | 清除 Token 后刷新 | 返回登录页 |
| 403/404 | B 请求 A 的 fileId | 访问被拒绝 |
| 伪造扩展名 | 修改扩展名后上传 | 返回 400 |
| 取消上传 | 上传中点击取消 | 会话被取消 |
| 清理 | 创建过期会话 | 会话和分片被清除 |

## 16. Range 验证

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
$response.Headers["Content-Range"]
$response.Headers["ETag"]
```

保存结果：

```text
状态码：206
Content-Range：bytes 0-99/文件大小
ETag：文件SHA-256
```

## 17. 双账号归属验证

1. 账号 A 登录。
2. A 上传文件。
3. 记录 `fileId`。
4. A 查看并下载文件。
5. 退出 A。
6. 账号 B 登录。
7. B 查看文件列表。
8. B 请求 A 的 `fileId`。
9. 记录 403 或项目约定的归属错误。
10. 重新登录 A。
11. 确认 A 的文件仍存在。

## 18. 伪造扩展名验证

1. 准备一个可执行文件或文本文件。
2. 将扩展名改为 `.pdf`。
3. 从页面上传。
4. 记录 HTTP 状态。
5. 确认状态为 400。
6. 在 MinIO 控制台确认没有生成新对象。

## 19. 截图和视频

截图保存到：

```text
docs/file-storage/evidence/
```

建议文件：

```text
01-small-upload.png
02-batch-upload.png
03-large-upload.png
04-paused.png
05-resume.png
06-range-206.png
07-owner-denied.png
08-spoofed-extension.png
```

演示视频顺序：

```text
登录
上传小文件
上传大文件
暂停
刷新
继续上传
合并完成
Range 下载
切换账号
验证归属
上传伪造扩展名
展示拒绝结果
```

## 20. GitHub 集成流程

每个模块通过 Pull Request 进入集成分支：

1. A 提交 `feat/file-backend-storage`。
2. C 提交 `feat/auth-frontend-session`。
3. B 提交 `feat/file-upload-engine`。
4. D 提交 `feat/file-ui-integration`。
5. D 逐个 Review 和合并。
6. D 在集成分支执行完整验收。
7. D 创建 `integration/file-storage -> main` Pull Request。

最终 Pull Request 包含：

```text
功能说明
接口清单
数据库迁移
环境变量
测试结果
验收截图
演示视频链接
已知后续优化
```

## 21. 完成定义

- 文件管理入口清楚。
- 文件列表与页面对 A 的接口。
- 上传功能复用 B 的组件。
- 登录功能使用 C 的正式认证。
- 小文件、批量、大文件、续传和下载全部可用。
- 两个账号的数据严格隔离。
- Range 返回 206，非法范围返回 416。
- 伪造扩展名返回 400。
- GitHub Pull Request、Issue 和验收记录完整。
- 集成分支通过 CI 后合入 `main`。
