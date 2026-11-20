# 文件上传引擎（B）

> 维护人：B（前端上传引擎）　消费者：D（页面接入）　评审：A（接口参数）、C（临时 Token 切换点）

接口前缀 `/api/v1/files`，请求头 `Authorization: Bearer <token>`，临时 Token 存储键 `kaoyan_access_token`（兼容旧键 `satoken` / `sa_token`）。
功能分支 `feat/file-upload-engine`，合入集成分支 `integration/file-storage`。

## 1. 文件清单

| 文件 | 职责 |
| --- | --- |
| `file.http.ts` | 上传专用 axios 实例：临时 Token、401/403 事件、错误归类（可重试判定） |
| `file.api.ts` | 全部文件接口（上传 / 会话 / 列表 / 详情 / 删除 / 下载地址） |
| `types.ts` | 文件与上传任务类型（字段对齐后端 `FileObject` / `FileUploadSession`） |
| `hash.worker.ts` | Worker 内增量 SHA-256；分片哈希优先 `crypto.subtle`，非安全上下文回落纯 JS |
| `uploader.ts` | 上传状态机：小文件 / 批量 / 分片 / 重试 / 暂停续传 / 本地会话记录 |
| `components/UploadTaskCenter.vue` | 上传任务中心组件（进度、按钮、续传提示） |

## 2. D 的接入方式

```vue
<UploadTaskCenter
  :files="files"
  :scene="scene"
  :auto-start="true"
  :concurrency="3"
  @completed="handleCompleted"
  @failed="handleFailed"
  @progress="handleProgress"
/>
```

| 属性 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `files` | `File[]` | `[]` | 用户选择的文件，数组引用变化即入队 |
| `scene` | `FileScene` | `STUDY_MATERIAL` | 文件场景，后端非法值返回 400 |
| `autoStart` | `boolean` | `true` | 入队后是否立刻开始 |
| `concurrency` | `number` | `3` | 同时上传的分片数（全引擎共享槽位） |

| 事件 | 载荷 | 说明 |
| --- | --- | --- |
| `completed` | `FileObject` | 单个文件上传（合并）成功 |
| `failed` | `(task, error)` | 任务失败，`task.error` 为可展示文案 |
| `progress` | `UploadTask` | 任意进度变化（D 可自行节流） |

建议 D 在 `MainLayout` 里挂一份组件，跨页面保持任务不中断；页面通过 `defineExpose` 暴露的 `engine` 可调用
`pauseTask / resumeTask / cancelTask / retryTask`。

## 3. 行为约定

- 阈值：`file.size <= 50MB` 走直传；超过走分片（分片 5MB，与后端一致）。
- 批量：同场景小文件按 **≤20 个/批 且 ≤50MB/批** 拆批，调 `POST /files/upload`。
- 分片：并发 3；失败按 1s / 2s / 4s 重试，最多 3 次；401 / 403 / 400 / 409 立即失败不重试。
- 暂停：不再启动新分片，在途分片结束，保留服务端会话；继续时先查会话再补缺失分片。
- 取消：取消服务端会话并删本地记录。
- 刷新续传：本地记录键 `kaoyan.file.upload-sessions.v1`，指纹 `文件名:大小:最后修改时间`（哈希算完后再核对）；
  用户重新选择文件后按名称/大小/最后修改时间匹配，再比对 SHA-256，一致才复用会话。

## 4. 集成说明

- **A 未交付前的开发方式**：`main` 上 `modules/file` 目前只有 `package-info.java` 和 README 占位，
  没有 `FileController`，所以联调阶段统一开 Mock（见 §6）；A 的 `/api/v1/files` 合并后只需关掉 Mock，不改编码。
- **A 侧字段缺口**：会话查询接口《B 指南》约定返回 `uploadedParts` / `missingParts`，若最终实现只给 `uploadedCount`，
  前端已兼容：有明细则按明细补片，没有则用本地记录求差；跨设备续传时会全量重传（后端 `upsertPart` 幂等，不会出错）。
- **C 侧切换点**：`file.api.ts` 改用 `shared/api/request.ts` → 删除 `file.http.ts` → 用 `authSession` 注入 Token → 重跑上传与 401 用例。
  `file.http.ts` 里 `LEGACY_TOKEN_KEYS` 只是兼容旧键，切换后随文件一起删除。

## 5. 联调前置

**仅 A 的接口可用后才需要**：浏览器控制台写入真实 Token（Mock 阶段不需要）：

```js
localStorage.setItem('kaoyan_access_token', '<粘贴真实 token>')
```

Token 获取：`POST /api/v1/auth/login`（登录页账号密码登录即可）。测试账号：`student1`（学生）、`admin`（管理员）。

## 6. Mock 联调

开关集中在 `file.mock.ts`，`file.api.ts` 里每个函数开头判断一次，切回真实接口不用改别处。

```js
// 开启（默认 normal：小文件固定 fileId 9001，大文件固定会话 7001，绝大多数分片已传、缺 3 片）
localStorage.setItem('kaoyan.file.mock', 'on')

// 场景：normal / unauthorized（401） / hash-error（400 分片哈希错误） / network（503 网络中断，可触发重试）
localStorage.setItem('kaoyan.file.mock.scenario', 'network')

// 关闭：删掉两个键，或 disableFileMock()
```

## 7. 测试

```bash
npm test          # 一次性跑完
npm run test:watch
```

`src/features/file/__tests__/uploader.spec.ts` 共 11 条，覆盖：

- 50MB 阈值判定（50MB 直传 / 50MB+1 分片）
- 拆批规则（25 个 → 20+5；20MB×3 → 2+1）
- 指纹格式（名称:大小:最后修改时间[:哈希]）
- 小文件直传全流程与 `completed(file)` 事件
- 51MB 分片：11 片、并发峰值 ≤3、合并成功、本地记录清空
- 25 个小文件按批上传（两批）
- 暂停 → 继续，仍能完成
- 取消 → `CANCELLED` 且调用服务端取消会话
- 401 → `FAILED` 并提示重新登录
- 分片 503 自动重试用例
- 本地续传记录的损坏降级与按 sessionId 删除

引擎依赖通过构造参数注入（`api` / `hasher` / `retryDelays`），所以单测不打网络、不起 Worker；
生产代码默认用真实 `file.api.ts` 和 Worker 哈希。
