# 文件模块

负责人：孙晔  
接口前缀：`/api/v1/files`（对齐《接口设计文档 V1.1》第 10 章）

## 已实现接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/files` | 小文件直传（`file` + `scene`），同用户同 SHA-256 秒传 |
| POST | `/api/v1/files/upload` | 批量直传（`files[]` + `scene`） |
| POST | `/api/v1/files/batch` | 按 `fileIds` 批量取元数据 |
| POST | `/api/v1/files/upload-sessions` | 初始化分片会话，`Idempotency-Key` 必填 |
| GET | `/api/v1/files/upload-sessions/{sessionId}` | 会话进度 |
| PUT | `/api/v1/files/upload-sessions/{sessionId}/parts/{partNumber}` | 上传分片（`part` 文件 + `X-Part-Hash`） |
| POST | `/api/v1/files/upload-sessions/{sessionId}/complete` | 合并分片（`scene` 参数），生成 `file_object` |
| DELETE | `/api/v1/files/upload-sessions/{sessionId}` | 取消会话并清分片记录 |
| GET | `/api/v1/files` | 我的文件分页（`scene` / `page` / `size`） |
| GET | `/api/v1/files/{fileId}` | 文件详情 |
| GET | `/api/v1/files/{fileId}/content` | 60 分钟预签名下载地址 |
| DELETE | `/api/v1/files/{fileId}` | 逻辑删除 + 删 MinIO 对象 |

## 持久化

- 文件本体：MinIO，桶名取 `minio.bucket`（默认 `kaoyan-files`）
- 元数据：`file_object`；上传会话与分片：`file_upload_session` / `file_upload_part`（V4 迁移）
- 场景枚举：`AVATAR / QUESTION_IMAGE / STUDY_MATERIAL / KNOWLEDGE_DOCUMENT / CHAT_ATTACHMENT`，非法 400

一致性：一律「先落对象再写库」，写库失败删对象；分片会话未完成时 `object_key` 暂存分片前缀
`tmp/{scene}/{userId}/{uuid}`，合并成功后替换为正式对象键；合并用条件 UPDATE 抢占 `MERGING`，
并发 complete 只有一个成功；过期会话由 `UploadCleanupJob` 每 10 分钟清理。
