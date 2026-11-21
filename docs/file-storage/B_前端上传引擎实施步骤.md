# 人员 B：前端上传引擎操作指南

> 读者：前端上传引擎负责人 B、组件消费者 D、代码评审人
> 目标：实现一套可复用的文件上传、分片、重试和断点续传能力
> 工作方式：使用 GitHub Issue 跟踪任务，使用功能分支开发，通过 Pull Request 合入集成分支

## 1. B 的工作职责

B 负责从浏览器选择文件到后端返回 `fileId` 的完整上传流程，交付：

- 文件接口封装。
- 小文件上传。
- 批量上传。
- SHA-256 计算。
- 大文件切片。
- 分片并发上传。
- 失败重试。
- 暂停、继续和取消。
- 刷新后续传。
- 上传任务中心和对外事件。
- 在 C 完成前的临时 Token 接入方式。

### 1.1 交付给 D 的组件能力

B 向 D 交付：

- `UploadTaskCenter.vue`。
- 上传任务数据结构和事件。
- 文件列表 API。
- 删除和下载 API 封装。
- 可独立调用的上传方法。

D 通过属性传入文件和场景，通过事件接收完成或失败结果。

### 1.2 文件维护责任

B 维护：

```text
frontend/src/features/file/file.http.ts
frontend/src/features/file/file.api.ts
frontend/src/features/file/types.ts
frontend/src/features/file/uploader.ts
frontend/src/features/file/hash.worker.ts
frontend/src/features/file/components/UploadTaskCenter.vue
```

C 维护：

```text
frontend/src/shared/auth/
frontend/src/shared/api/request.ts
frontend/src/features/auth/
```

D 维护：

```text
frontend/src/features/file/FilePage.vue
frontend/src/app/layouts/MainLayout.vue
frontend/src/features/chat/ChatPage.vue
frontend/src/features/studyplan/StudyPlanPage.vue
```

模块之间通过 Issue、接口文档和 Pull Request Review 协调变更。

## 2. GitHub 分支规划

### 2.1 分支结构

```text
main
└── integration/file-storage
    ├── feat/file-upload-engine
    ├── feat/file-backend-storage
    ├── feat/auth-frontend-session
    └── feat/file-ui-integration
```

B 的主要分支：

```text
feat/file-upload-engine
```

### 2.2 创建 GitHub Issue

Issue 标题：

```text
[FILE-B] 前端上传引擎、分片重试和断点续传
```

Issue 内容：

```text
上传阈值
小文件和批量流程
分片并发数
重试策略
暂停/继续/取消语义
刷新续传流程
交给 D 的组件接口
测试清单
完成定义
```

建议标签：

```text
area:frontend
feature:file-storage
component:uploader
priority:high
```

### 2.3 创建功能分支

在仓库根目录执行：

```powershell
git fetch origin
git switch integration/file-storage
git pull --ff-only
git switch -c feat/file-upload-engine
git push -u origin feat/file-upload-engine
```

### 2.4 Pull Request 规则

Pull Request 目标分支：

```text
integration/file-storage
```

Pull Request 中提供：

- 上传流程图。
- 组件属性和事件表。
- 50MB 阈值说明。
- 分片并发和重试策略。
- 刷新续传测试结果。
- 临时认证接入说明。
- 测试截图或录屏。
- 关联 Issue。

评审人：

- D 评审组件接口和页面接入方式。
- A 评审文件接口调用参数。
- C 评审临时 Token 与正式认证的切换点。

### 2.5 提交拆分

建议提交：

```text
feat(file): 封装文件接口和类型定义
feat(file): 实现小文件与批量上传
feat(file): 实现分片哈希和并发上传
feat(file): 实现暂停续传和失败重试
feat(file): 增加上传任务中心
test(file): 增加上传引擎状态测试
docs(file): 补充上传组件接入说明
```

## 3. 创建上传模块

在仓库根目录进入前端：

```powershell
cd frontend
npm install
```

建立目录：

```text
src/features/file/
  file.http.ts
  file.api.ts
  types.ts
  uploader.ts
  hash.worker.ts
  components/
    UploadTaskCenter.vue
```

职责：

| 文件 | 职责 |
|---|---|
| `file.http.ts` | 开发阶段的临时 Token 和 Axios 实例 |
| `file.api.ts` | 所有文件接口调用 |
| `types.ts` | 文件和上传任务类型 |
| `uploader.ts` | 切片、并发、重试和续传状态 |
| `hash.worker.ts` | 大文件哈希计算 |
| `UploadTaskCenter.vue` | 上传任务展示 |

## 4. C 完成前的临时认证接入

C 的登录页面开发期间，B 通过真实 Token 完成联调。

### 4.1 开发 Token 存储

约定键：

```text
kaoyan_access_token
```

开发人员从后端登录接口取得真实 Token，在浏览器控制台执行：

```js
localStorage.setItem('kaoyan_access_token', '真实Token')
```

### 4.2 临时客户端

`file.http.ts` 创建独立 Axios 实例：

```ts
const TOKEN_KEY = 'kaoyan_access_token'
```

请求拦截器读取 Token，并添加：

```http
Authorization: Bearer <token>
```

分片请求超时设置为 60 秒以上：

```ts
timeout: 60000
```

响应拦截器处理：

- 401：清除开发 Token，触发登录失效通知。
- 403：保留 Token，触发无权限通知。
- 其他错误：将后端 `message` 传给上传任务。

### 4.3 正式切换点

C 完成共享认证后，由 D 在集成 Pull Request 中：

1. 将 `file.api.ts` 改用 `shared/api/request.ts`。
2. 删除对 `fileClient` 的引用。
3. 删除 `file.http.ts`。
4. 使用 C 的 `authSession` 完成 Token 注入。
5. 重新执行上传和 401 测试。

## 5. 定义数据类型

在 `types.ts` 中定义：

```ts
export type FileScene =
  | 'AVATAR'
  | 'QUESTION_IMAGE'
  | 'STUDY_MATERIAL'
  | 'KNOWLEDGE_DOCUMENT'
  | 'CHAT_ATTACHMENT'

export type UploadStatus =
  | 'WAITING'
  | 'HASHING'
  | 'UPLOADING'
  | 'PAUSED'
  | 'MERGING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface FileObject {
  id: number
  scene: FileScene
  originalFilename: string
  fileExt?: string
  contentType: string
  fileSize: number
  sha256?: string
  status: string
  createTime: string
}
```

字段名称与 A 的响应 DTO 保持一致。D 的页面直接复用这些类型。

## 6. 封装文件 API

`file.api.ts` 提供：

```text
uploadSmall(file, scene)
uploadBatch(files, scene)
initUploadSession(meta)
getUploadSession(sessionId)
uploadPart(sessionId, partNumber, blob, hash)
completeUploadSession(sessionId, scene)
cancelUploadSession(sessionId)
listFiles(query)
getFile(fileId)
deleteFile(fileId)
getDownloadUrl(fileId)
```

请求路径使用：

```text
/files
/files/upload
/files/upload-sessions
```

初始化会话携带：

```http
Idempotency-Key: <UUID>
```

同一次逻辑操作重试时复用相同幂等键，新任务生成新键。

## 7. 小文件上传

阈值：

```text
file.size <= 50MB
```

流程：

1. 用户选择文件。
2. 计算 SHA-256。
3. 创建 FormData。
4. 追加上传文件。
5. 追加 `scene`。
6. 调用 `POST /files`。
7. 接收文件对象。
8. 发出 `completed(file)`。

批量上传流程：

1. 前端检查文件数量和总大小。
2. 按最多 20 个文件拆批。
3. 按总大小不超过 50MB 拆批。
4. 逐批调用 `POST /files/upload`。
5. 合并每批返回结果。
6. 发出总进度和完成事件。

## 8. 大文件哈希

### 8.1 分片哈希

每个分片调用：

```ts
crypto.subtle.digest('SHA-256', chunkBuffer)
```

转换为 64 位小写十六进制，放入：

```http
X-Part-Hash
```

### 8.2 整文件哈希

使用 `hash.worker.ts` 以增量方式计算哈希，将大文件处理留在 Worker 线程。

哈希期间状态：

```text
HASHING
```

哈希完成后：

- 设置文件指纹。
- 初始化上传会话。
- 将整文件哈希传给 `fileHash`。
- 进入分片上传状态。

项目标准使用 SHA-256。验收明确要求 MD5 时增加 MD5 字段和校验，同时保留 SHA-256。

## 9. 分片上传

默认分片大小：

```text
5242880
```

切片：

```ts
const start = (partNumber - 1) * partSize
const end = Math.min(start + partSize, file.size)
const blob = file.slice(start, end)
```

表单：

```ts
const form = new FormData()
form.append('part', blob, `${partNumber}.part`)
```

请求：

```ts
await fileClient.put(
  `/files/upload-sessions/${sessionId}/parts/${partNumber}`,
  form,
  { headers: { 'X-Part-Hash': hash } }
)
```

并发策略：

```text
同时上传 3 个分片
```

主上传器维护：

- 待上传分片列表。
- 正在上传分片集合。
- 已完成分片集合。
- 失败分片映射。
- 当前任务状态。

## 10. 失败重试

分片失败后记录错误并重新加入队列。

等待策略：

```text
第 1 次重试：1 秒
第 2 次重试：2 秒
第 3 次重试：4 秒
```

自动重试适用于：

- 网络超时。
- 连接中断。
- 502/503/504。

立即停止并展示错误的场景：

- 401。
- 403。
- 400 分片大小错误。
- 400 哈希错误。
- 409 会话状态冲突。

## 11. 暂停、继续和取消

### 11.1 暂停

1. 停止从队列启动新分片。
2. 等待已发出的分片请求结束。
3. 将任务状态改为 `PAUSED`。
4. 保存任务和 sessionId。
5. 保留服务端会话。

### 11.2 继续

1. 调用 `GET /files/upload-sessions/{sessionId}`。
2. 读取 `uploadedParts` 和 `missingParts`。
3. 更新本地完成集合。
4. 将缺失分片重新加入队列。
5. 将任务状态改为 `UPLOADING`。

### 11.3 取消

1. 清空待上传队列。
2. 停止后续分片。
3. 调用 `DELETE /files/upload-sessions/{sessionId}`。
4. 将本地状态改为 `CANCELLED`。
5. 删除本地会话记录。

## 12. 刷新后的断点续传

本地保存以下信息：

```json
{
  "fingerprint": "文件名:大小:最后修改时间:哈希",
  "sessionId": 123,
  "scene": "STUDY_MATERIAL",
  "status": "PAUSED"
}
```

存储键：

```text
kaoyan.file.upload-sessions.v1
```

刷新流程：

1. 从本地存储读取未完成会话。
2. 调用会话查询接口。
3. 获取 `uploadedParts` 和 `missingParts`。
4. 提示用户重新选择对应文件。
5. 比较名称、大小、修改时间和哈希。
6. 文件匹配时只上传缺失分片。
7. 文件不匹配时创建新的上传会话。

## 13. 完成合并

分片全部上传后：

1. 状态修改为 `MERGING`。
2. 调用 complete 接口。
3. 成功后接收 `FileObject`。
4. 删除本地会话记录。
5. 发出 `completed(file)`。
6. 失败时保留会话并展示错误。
7. 用户点击重试时再次调用 complete。

## 14. 上传任务中心

组件输入：

```text
files
scene
autoStart
concurrency
```

组件事件：

```text
completed(file)
failed(task, error)
progress(task)
```

组件显示：

- 文件名。
- 文件大小。
- 当前状态。
- 上传百分比。
- 分片进度。
- 暂停、继续、取消、重试按钮。
- 错误信息。

## 15. Mock 联调

A 的接口完成前，B 使用 Mock：

- 小文件返回固定 `fileId`。
- 大文件返回固定会话和分片数量。
- 模拟部分分片成功。
- 模拟刷新后缺少 3 个分片。
- 模拟 401、400 哈希错误和网络中断。

Mock 开关集中在一个模块，集成完成后切换为真实接口。

## 16. 测试清单

| 场景 | 预期结果 |
|---|---|
| 1MB 文件 | 小文件上传成功 |
| 5 个小文件 | 批量上传成功 |
| 51MB 文件 | 自动进入分片模式 |
| 100MB 文件 | 正常计算哈希和并发上传 |
| 上传中暂停 | 停止启动新分片 |
| 暂停后继续 | 只上传缺失分片 |
| 刷新后选同一文件 | 恢复原会话 |
| 刷新后选不同文件 | 创建新会话 |
| 单个分片失败 | 自动重试 |
| Token 失效 | 停止上传并通知登录失效 |
| 用户取消 | 服务端会话取消 |
| 重复 complete | 接收同一文件对象 |

## 17. GitHub 交付

Pull Request 应包含：

- 组件目录。
- 上传 API。
- 状态机测试。
- Mock 说明。
- 截图或录屏。
- 与 D 的组件接口说明。
- 与 C 的正式认证切换点。

合并后 B 在 Issue 中记录：

```text
已交付提交
组件接口
测试结果
遗留事项
关联文档
```

## 18. 完成定义

- 小文件、批量和分片上传可用。
- 分片并发数可配置。
- 分片失败可以重试。
- 暂停、继续、取消语义清楚。
- 刷新后只补缺失分片。
- D 可以使用统一组件完成三个业务入口。
- C 完成后通过一次连接层切换启用正式认证。
- 上传状态机测试和集成测试通过。
