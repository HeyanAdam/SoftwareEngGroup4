# 人员 A：文件后端与 MinIO 操作指南

> 读者：文件存储后端负责人 A、代码评审人和集成负责人
> 目标：完成文件对象、上传会话、分片合并、下载和安全校验的后端闭环
> 工作方式：使用 GitHub Issue 跟踪任务，使用功能分支开发，通过 Pull Request 合入集成分支

## 1. A 的工作职责

A 负责文件存储后端从接收请求到写入对象存储的完整流程，交付以下能力：

- 配置并验证 MinIO。
- 实现小文件单文件上传。
- 实现小文件批量上传和数据库批量写入。
- 实现大文件上传会话。
- 保存并校验分片大小、序号、哈希和状态。
- 合并分片并生成正式文件对象。
- 实现普通下载、Range 下载、206 和 ETag。
- 实现文件归属、权限、扩展名和 MIME 校验。
- 清理失败会话、过期会话和孤立分片。
- 编写后端测试，并向 B、D 提供稳定接口说明。

### 1.1 协作交接

A 向 B 交付以下接口行为：

- 初始化上传会话后返回 `sessionId`、`partSize` 和 `partCount`。
- 查询会话时返回 `uploadedParts` 和 `missingParts`。
- 上传分片后返回最新上传进度。
- 合并完成后返回 `FileObject`。

A 向 D 交付以下验证能力：

- 文件列表接口。
- 文件详情接口。
- 普通下载接口。
- Range 下载接口。
- 删除接口。
- 用户 A 和用户 B 的归属隔离结果。

### 1.2 文件维护责任

A 维护以下目录：

```text
backend/src/main/java/com/group4/kaoyan/modules/file/
backend/src/main/java/com/group4/kaoyan/platform/storage/
backend/src/main/resources/mapper/file/
backend/src/main/resources/db/migration/
```

A 可以更新以下配置：

```text
backend/pom.xml
backend/src/main/resources/config/application.yml
deploy/docker-compose.yml
deploy/.env.example
```

其他成员通过 GitHub Issue 或 Pull Request Review 提出接口变更。对应文件由 A 统一提交，保持接口和实现同步。

## 2. GitHub 分支规划

### 2.1 总体分支结构

文件存储使用一条集成分支汇总各成员成果：

```text
main
└── integration/file-storage
    ├── feat/file-backend-storage       A
    ├── feat/file-upload-engine         B
    ├── feat/auth-frontend-session      C
    └── feat/file-ui-integration        D
```

`integration/file-storage` 由集成负责人 D 创建和维护。A 的所有文件后端提交进入自己的功能分支，再通过 Pull Request 合入集成分支。

### 2.2 创建 GitHub Issue

A 在 GitHub 创建或认领以下 Issue：

```text
[FILE-A] 后端文件存储、MinIO、续传和下载
```

Issue 描述至少包含：

```text
目标
接口清单
数据库影响
错误码
测试清单
完成定义
关联 PR
```

建议添加标签：

```text
area:backend
feature:file-storage
component:minio
priority:high
```

### 2.3 创建功能分支

在仓库根目录执行：

```powershell
git fetch origin
git switch integration/file-storage
git pull --ff-only
git switch -c feat/file-backend-storage
git push -u origin feat/file-backend-storage
```

如果集成分支尚未建立，由 D 先创建：

```powershell
git switch main
git pull --ff-only
git switch -c integration/file-storage
git push -u origin integration/file-storage
```

### 2.4 提交规划

按功能拆成独立提交：

```text
chore(file): 配置 MinIO 运行环境和依赖
feat(file): 完成小文件与批量事务写入
feat(file): 补充分片哈希与合并校验
feat(file): 支持 Range 下载和 ETag
feat(file): 增加文件权限与类型校验
test(file): 增加文件存储验收测试
docs(file): 更新后端接口交接说明
```

### 2.5 Pull Request 规则

A 创建 Pull Request，目标分支选择：

```text
integration/file-storage
```

Pull Request 中写明：

- 完成的接口。
- 数据库迁移版本。
- MinIO 配置变化。
- 使用的错误码。
- 已执行的测试。
- 需要 B、D、C 配合的事项。
- GitHub Issue 链接。

评审重点：

- B 检查会话和分片响应。
- D 检查列表、下载和删除接口。
- C 检查用户身份和权限来源。

所有 CI 和评审通过后，A 将 Pull Request 合入集成分支。

## 3. 开始开发

从仓库根目录进入后端：

```powershell
cd backend
```

先阅读现有实现：

```text
src/main/java/com/group4/kaoyan/modules/file/
src/main/java/com/group4/kaoyan/platform/storage/
src/main/resources/mapper/file/
src/main/resources/db/migration/V4__file_storage.sql
../docs/02_数据设计/02_文件与任务表.md
```

V4 已经作为历史迁移使用。后续表结构变化通过新版本迁移实现，例如 `V8__file_storage_enhancement.sql`。

## 4. 配置 MinIO

### 4.1 启用容器服务

在 `deploy/docker-compose.yml` 中启用 MinIO 服务，配置以下端口：

```text
9000：MinIO API
9001：MinIO Console
```

### 4.2 配置环境变量

在 `deploy/.env.example` 中配置：

```text
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=kaoyan-files
```

容器管理员变量与后端访问变量保持值一致：

```text
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

### 4.3 声明 SDK 依赖

在 `backend/pom.xml` 中显式声明 MinIO SDK：

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>${minio.version}</version>
</dependency>
```

### 4.4 启动和检查

在 `deploy` 目录执行：

```powershell
docker compose up -d mysql redis rabbitmq minio
docker compose ps
docker compose logs minio --tail 100
```

检查服务：

```text
http://localhost:9000/minio/health/live
http://localhost:9001
```

登录 MinIO 后确认 `kaoyan-files` 桶存在。桶保持私有，下载通过后端鉴权接口或短时预签名地址完成。

## 5. 获取后端测试 Token

在仓库根目录启动后端：

```powershell
cd backend
mvn spring-boot:run
```

打开 Swagger：

```text
http://localhost:8080/swagger-ui/index.html
```

按当前合并后的认证契约执行登录。最终认证路径参考 C 的交接文档，文件接口路径固定为：

```text
/api/v1/files
```

PowerShell 获取 Token 的通用形式：

```powershell
$body = @{
  username = "测试账号"
  password = "测试密码"
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/认证登录路径" `
  -ContentType "application/json" `
  -Body $body

$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }
```

验证文件列表：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/files" `
  -Headers $headers
```

## 6. 完成小文件上传

### 6.1 单文件接口

```http
POST /api/v1/files
Content-Type: multipart/form-data

file=<文件>
scene=STUDY_MATERIAL
```

后端执行顺序：

1. 从登录态取得 `userId`。
2. 检查文件大小不超过 50MB。
3. 校验扩展名和文件头。
4. 计算 SHA-256。
5. 查询当前用户是否已有相同哈希的 READY 文件。
6. 命中秒传时返回已有文件。
7. 未命中时生成对象键并写入 MinIO。
8. 写入 `file_object`。
9. 返回文件元数据。

### 6.2 对象键格式

使用：

```text
{scene}/{userId}/{yyyy/MM}/{uuid}.{ext}
```

示例：

```text
STUDY_MATERIAL/10001/2026/11/f52d7f1e.pdf
```

## 7. 完成批量上传和批量写库

接口：

```http
POST /api/v1/files/upload
Content-Type: multipart/form-data

files[]=<文件1>
files[]=<文件2>
scene=STUDY_MATERIAL
```

实现步骤：

1. 预检查文件数量和总大小。
2. 单批最多接收 20 个文件。
3. 单批总大小控制在 50MB 以内。
4. 校验所有文件的类型和大小。
5. 将新文件写入 MinIO。
6. 在一个事务中执行批量 INSERT。
7. 批量写库失败时删除本次新写入的对象。
8. 返回每个文件的 `fileId` 和状态。

建议在 Mapper 中增加 `batchInsert(List<FileObject>)`，使用一条多行 INSERT。

## 8. 完成大文件上传会话

### 8.1 初始化

```http
POST /api/v1/files/upload-sessions
Idempotency-Key: <UUID>
Content-Type: application/json
```

请求示例：

```json
{
  "filename": "复习资料.pdf",
  "fileSize": 104857600,
  "contentType": "application/pdf",
  "partSize": 5242880,
  "fileHash": "整文件SHA-256",
  "scene": "STUDY_MATERIAL"
}
```

后端计算：

```text
partCount = ceil(fileSize / partSize)
```

会话默认有效期设置为 24 小时。

### 8.2 查询进度

```http
GET /api/v1/files/upload-sessions/{sessionId}
```

返回 DTO：

```json
{
  "sessionId": 123,
  "filename": "复习资料.pdf",
  "fileSize": 104857600,
  "partSize": 5242880,
  "partCount": 20,
  "uploadedCount": 7,
  "uploadedParts": [1, 2, 3, 4, 6, 7, 8],
  "missingParts": [5, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20],
  "status": "UPLOADING",
  "expiresAt": "2026-11-21T12:00:00"
}
```

响应 DTO 只包含前端需要的字段，数据库对象键留在后端。

### 8.3 上传分片

```http
PUT /api/v1/files/upload-sessions/{sessionId}/parts/{partNumber}
Content-Type: multipart/form-data
X-Part-Hash: <分片SHA-256>

part=<分片文件>
```

后端执行：

1. 检查会话归属。
2. 检查会话状态为 `INIT` 或 `UPLOADING`。
3. 检查分片序号范围。
4. 检查分片大小与预期一致。
5. 计算分片实际哈希。
6. 比较 `X-Part-Hash`。
7. 写入分片对象。
8. upsert `file_upload_part`。
9. 重新统计 `uploadedCount`。
10. 返回最新会话进度。

`uk_upload_part(session_id, part_number)` 保证同一分片重传后只保留一条当前记录。

### 8.4 合并

```http
POST /api/v1/files/upload-sessions/{sessionId}/complete?scene=STUDY_MATERIAL
```

后端执行：

1. 检查所有分片为 `UPLOADED`。
2. 按 `partNumber` 升序读取分片。
3. 通过条件 UPDATE 将会话推进到 `MERGING`。
4. 调用 MinIO compose。
5. 读取合并后的对象并计算整文件 SHA-256。
6. 比较初始化时的 `fileHash`。
7. 写入正式 MIME、大小和扩展名。
8. 在数据库事务中插入 `file_object`。
9. 标记会话为 `COMPLETED`。
10. 事务成功后删除临时分片。
11. 返回正式 `FileObject`。

合并失败时将会话恢复为可重试状态，并保留分片供再次 complete。

## 9. 完成下载和 Range

保留预签名接口：

```http
GET /api/v1/files/{fileId}/content
```

增加后端流式下载接口：

```http
GET /api/v1/files/{fileId}/download
Authorization: Bearer <token>
Range: bytes=0-99
If-Range: "<ETag>"
```

响应要求：

```text
200：完整下载
206：局部下载
416：范围非法
Accept-Ranges: bytes
Content-Range: bytes start-end/size
Content-Type: 已验证MIME
ETag: "<文件SHA-256>"
```

后端执行：

1. 按 `fileId + owner_user_id` 查询 READY 文件。
2. 解析单段 Range。
3. 检查 start/end 范围。
4. 检查 `If-Range`。
5. 从 MinIO 读取指定偏移和长度。
6. 写入响应流。
7. 设置中文文件名响应头。

## 10. 完成安全校验

### 10.1 权限

使用合并后的权限常量类，为方法添加：

```text
file:view
file:upload
file:download
file:delete
```

### 10.2 文件归属

所有用户文件查询都带 `owner_user_id = 当前用户`。文件详情、下载、删除和批量取元数据使用同一归属条件。

### 10.3 类型校验

建立白名单：

```text
pdf
png
jpg
jpeg
doc
docx
txt
md
```

校验规则：

- 扩展名统一转小写。
- 文件名去掉目录部分。
- MIME 由后端识别并保存。
- 文件头与扩展名匹配。
- 文件头不匹配时返回 400。

## 11. 完成清理

清理任务处理：

- `INIT/UPLOADING` 且 `expires_at < NOW()` 的会话。
- `FAILED/CANCELLED` 会话的分片对象。
- 合并成功后的临时分片。
- 数据库有记录但 MinIO 对象缺失的异常文件。

清理任务按会话逐条处理，单条失败记录日志，下一轮继续执行。

## 12. 后端测试

### 12.1 单元测试

测试以下逻辑：

- 文件名清理。
- 扩展名解析。
- 分片数量和大小计算。
- Range 头解析。
- ETag 比较。
- 文件类型识别。
- 批量写库回滚。

### 12.2 集成测试

测试以下场景：

| 场景 | 预期结果 |
|---|---|
| 无 Token 上传 | 401 |
| 小文件上传 | 写入 MinIO 和 MySQL |
| 同用户同哈希上传 | 秒传 |
| 批量上传 | 一次事务写入 |
| 分片哈希错误 | 400 |
| 缺少分片完成 | 409 |
| 分片乱序上传 | 合并顺序正确 |
| 重复 complete | 返回同一文件 |
| 用户 B 访问用户 A 文件 | 403 或项目约定状态 |
| Range 0-99 | 206 |
| 非法 Range | 416 |
| 伪造扩展名 | 400 |
| 过期会话 | 被清理 |

## 13. 交付和验收

A 的 Pull Request 合并条件：

- GitHub Issue 已关联。
- MinIO 启动说明已更新。
- 数据库迁移和接口文档已更新。
- B 能使用会话进度接口完成续传。
- D 能使用列表、下载和删除接口完成页面。
- C 能通过正式 Token 完成归属测试。
- CI、代码评审和接口验收通过。

最终交付内容：

```text
后端实现
数据库迁移
MinIO 配置
接口说明
测试用例
测试结果
Pull Request
```

## 14. 完成定义

A 的工作在以下条件全部成立时完成：

- MinIO 可通过 Docker 启动并持久化数据。
- 小文件和批量文件上传成功。
- 大文件可以初始化、上传分片、查询缺片和合并。
- 分片哈希和整文件哈希均通过校验。
- 普通下载和 Range 下载正常。
- 文件归属与接口权限生效。
- 伪造扩展名被拒绝。
- 清理任务正常工作。
- 后端测试通过。
- B、D、C 已收到最终接口说明。
