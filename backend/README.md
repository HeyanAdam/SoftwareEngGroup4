# AI 考研 RAG 系统 · 后端

Spring Boot 3 + MyBatis-Plus + Sa-Token(RBAC) + Milvus + MinIO + 模型 API（OpenAI 兼容）。
所有环境相关参数外置，换机器只需改 `.env`。

## 技术栈

| 组件 | 说明 |
| --- | --- |
| Java 17 / Spring Boot 3.2.5 | Web、Validation、WebFlux(WebClient 流式) |
| MyBatis-Plus 3.5.7 | 单表 CRUD 与分页 |
| MySQL 8 | 业务数据 + 文档解析后全文 |
| Redis 7 | 预留缓存/会话（当前主要给 Sa-Token 扩展用） |
| Sa-Token 1.38 | 登录态与 RBAC 注解鉴权 |
| Milvus 2.4 | 向量检索（COLLECTION `doc_chunk`，COSINE + HNSW） |
| MinIO | 原始上传文件（PDF/DOCX/TXT） |
| 模型 API | 对话 `/chat/completions`、向量 `/embeddings`，模型名可配 |

## 目录

```
backend/
└── src/main/java/com/group4/kaoyan
    ├── KaoyanApplication.java
    ├── common/                    统一响应 R、业务异常、全局异常、分页、config（配置属性/拦截器/线程池）
    ├── platform/                  基础能力
    │   ├── ai/                    模型 API 与向量库：llm、embedding、vectorstore(Milvus)
    │   ├── storage/               MinIO 文件存储
    │   ├── cache/                 Redis 能力（预留）
    │   ├── messaging/             RabbitMQ 能力（预留）
    │   └── realtime/              WebSocket 能力（预留）
    └── modules/                   业务模块
        ├── auth/                  登录、RBAC（角色/权限表、StpInterface、种子数据）
        ├── user/                  用户资料（SysUser、用户信息/改密）
        ├── studyplan/             学习计划（预留）
        ├── file/                  文件功能（预留）
        ├── chat/                  会话、AI 问答（SSE 流式）
        ├── knowledge/             知识库、文档解析切片、检索与 Prompt
        └── admin/                 后台：用户/角色权限管理、向量库重建
```

配置文件单独归置，不散落在项目根目录：

```
backend/
├── env/                                环境变量文件
│   ├── .env.example                    复制为 env/.env 使用（已被 .gitignore 忽略）
│   └── README.md
└── src/main/resources/
    ├── config/
    │   ├── application.yml             Spring Boot 默认会读 classpath:/config/
    │   ├── application-dev.yml
    │   └── application-prod.yml
    ├── db/schema.sql                   建表脚本（compose 首次启动自动执行）
    └── prompts/rag-system.txt          Prompt 模板
```

模块间依赖方向：`modules → platform → common`，业务模块之间只通过 service/entity 调用，
不直接跨模块访问 mapper（admin 例外，它本身是权限维护入口）。

## 一键起中间件

全项目只有**一份**环境配置：`deploy/.env`（`deploy/docker-compose.yml` 里已包含
MySQL / Redis / RabbitMQ / MinIO / Milvus(+etcd) / Mailpit / Nginx）。

```powershell
cd ..\deploy
Copy-Item .env.example .env     # 只填 LLM_API_KEY、EMBEDDING_API_KEY 即可
docker compose up -d
docker compose ps
```

后端本地跑时**自动读这份 `deploy/.env`**（`application.yml` 里的 `spring.config.import`），
不用再复制第二份、也不用手工 export 环境变量。

想把后端也容器化（可选）：

```powershell
docker compose --profile app up -d --build
```

## 本地启动

1. 确认 `deploy/.env` 存在（见上一节），填好两个模型 Key；想用自己的 Key 又不影响别人时，
   复制 `backend/env/.env.example` 为 `backend/env/.env` 只写覆盖项。
2. **建表不用手动做**：`spring.sql.init` 每次启动执行 `src/main/resources/db/schema.sql`
   （全 `CREATE TABLE IF NOT EXISTS`，可重复执行）。
3. 直接启动，无需手工设环境变量：

```powershell
mvn spring-boot:run
```

> IDE 里跑时工作目录一般是 `backend/`，`../deploy/.env` 能被找到；从仓库根目录跑 jar 也能找到
> `./deploy/.env`。两种路径都已配置。

4. 访问 http://localhost:8080/swagger-ui.html（dev 环境开启）。
5. 启动时只补 RBAC 字典（角色 ADMIN/STUDENT、11 条权限码），**不预置任何账号**。管理员是
   “第一个注册的人”：`POST /api/auth/register` 注册成功后，如果库里还没有管理员，这个用户
   直接拿到 ADMIN；之后注册的一律是 STUDENT，需要管理员在后台提权。

## 账号与权限的初始化流程

| 步骤 | 发生什么 | 持久化 |
| --- | --- | --- |
| 首次启动 | 建角色 `ADMIN` / `STUDENT`、11 条权限码；角色只在**第一次创建**时写入默认权限 | 落 MySQL，后续启动只读不写 |
| 第一次注册 | 库里没有 ADMIN 用户 → 该用户自动成为管理员（只发生一次） | `sys_user_role` 记录，重启不丢 |
| 之后注册 | 一律 `STUDENT`，只有 `chat:send` | 同上 |
| 提权 / 降权 | 管理员调 `PUT /api/admin/users/{id}/roles` 改用户的角色 | 同上 |
| 调权限 | 管理员调 `PUT /api/admin/roles/{id}/permissions` 改角色拥有的权限码 | 同上 |

重启不会被覆盖：`RbacInitializer` 只补“缺的东西”（缺的权限码、缺的角色），已存在的角色权限与
用户角色绑定一律不动，所以控制台里改过的配置可以一直用下去。

唯一管理员不能被降权：`AdminService.assignRoles` 会拦下来（否则谁都没法再进后台），要换管理员
请先把新管理员提权。

> 注意：`POST /api/auth/register` 是匿名开放的，谁先注册谁是管理员。正式环境里，数据库初始化
> 完、拿到第一个管理员后，建议在网关层关掉这个接口或加邀请码，否则别人抢先注册就拿到了管理权。

> 想重置 STUDENT 的权限为出厂值：在后台把 STUDENT 的权限改回只要 `chat:send`，或直接改库
> `sys_role_permission`。代码层面还有 `KnowledgeService.requireAdmin(...)` 兜底，即使权限码配错，
> 普通用户也进不了知识库接口。

## 关键接口

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| POST | `/api/auth/register` | 匿名 |
| POST | `/api/auth/login` | 匿名 |
| GET | `/api/auth/me` | 登录 |
| GET | `/api/kb` | kb:view |
| POST | `/api/kb` | kb:create |
| DELETE | `/api/kb/{id}` | kb:delete |
| POST | `/api/kb/{id}/documents` | doc:upload |
| GET | `/api/kb/{id}/documents` | kb:view |
| POST | `/api/doc/{id}/reparse` | doc:upload |
| DELETE | `/api/doc/{id}` | doc:delete |
| POST | `/api/chat/stream` | chat:send（SSE） |
| GET | `/api/chat/conversations` | 登录 |
| GET | `/api/admin/users` | admin:user:list |
| PUT | `/api/admin/users/{id}/roles` | admin:user:update |
| GET | `/api/admin/permissions` | admin:role:list |
| PUT | `/api/admin/roles/{id}/permissions` | admin:role:update |
| POST | `/api/admin/vector/reindex/{kbId}` | admin:vector:manage |

鉴权头：`Authorization: Bearer <token>`（`sa-token.token-prefix` 已设为 Bearer）。

## 权限模型：管理员管理 RAG 库，普通用户只能“用”数据

| 能力 | ADMIN | STUDENT（普通用户） |
| --- | --- | --- |
| 问答（内部检索向量、拿切片当依据） | ✅ | ✅（唯一入口，`chat:send`） |
| 引用信息（文档名 / 文档 id / 切片 id / 相似度 / 摘要） | ✅ | ❌ 返回空数组 |
| 浏览知识库列表 / 知识库详情 | ✅ | ❌ |
| 文档列表 / 文档原文（`raw_text`） | ✅ | ❌ |
| 建库、删库、上传、删除文档、重解析、重建向量 | ✅ | ❌ |
| 用户 / 角色 / 权限维护 | ✅ | ❌ |

一句话：**普通用户只能通过 `/api/chat/stream` 这个业务出口间接用到库里的数据，看不到库、文档、
切片和向量这些内部结构。**

实现上做了四层，缺一层都可能漏：

1. **权限码**：`kb:view`、`kb:create`、`kb:delete`、`doc:upload`、`doc:delete`、`admin:*` 只授予
   ADMIN；STUDENT 只有 `chat:send`。所有 `/api/kb`、`/api/doc` 接口都会被拦在 403。
2. **服务层**：`KnowledgeService` 的读方法（列表 / 详情 / 文档列表 / 原文）与写方法一律先
   `requireAdmin(...)`，权限码被误配时也不会漏。
3. **检索侧**：普通用户不认 `kbId`（传了也忽略），统一按“公开库 `kb_id` 集合”检索；管理员才按指定
   库或全量检索。库结构不暴露给普通用户。
4. **输出侧**：`references` 事件与落库的 `references_json` 按角色分级——普通用户直接返回空数组
   `[]`（连文档名都不给），管理员才有 `docName / docId / chunkId / score / snippet`。

`STUDENT` 是内置的默认注册角色，初始权限就是 `chat:send`。它的权限只在**角色第一次创建**时
写入，之后完全以数据库为准——管理员在后台加或减权限，重启都不会被还原。

## SSE 事件约定（POST /api/chat/stream）

请求体：`{"conversationId":1,"kbId":2,"question":"..."}`。
`conversationId` 为空则新建会话；`kbId` 仅对管理员生效，普通用户传了也会被忽略（固定检索公开库）。

事件顺序：

| event | data |
| --- | --- |
| `meta` | `{"conversationId":1,"model":"deepseek-chat"}` |
| `references` | 管理员：`[{"index":1,"docName":"xx.pdf","docId":3,"chunkId":12,"score":0.83,"snippet":"..."}]`；普通用户：`[]` |
| `delta` | `{"content":"逐字增量"}` |
| `done` | `{"messageId":8,"error":null}` |

前端用 `fetch` + `ReadableStream` 消费（`EventSource` 只支持 GET，不适用于带 body 的问答）；
按 `\n\n` 切帧，再解析 `event:` 与 `data:` 两行即可。

## 环境与注意事项

- **模型名可配**：`LLM_MODEL`、`EMBEDDING_MODEL` 都来自环境变量，换模型只改配置，不动代码。
- **维度必须一致**：`EMBEDDING_DIMENSION` 与 Milvus collection 维度要相同；换向量模型后请删除旧
  collection 让它按新维度重建。
- **Milvus 适配**：依赖 `milvus-sdk-java:2.4.1`，`MilvusVectorStore` 的 insert/search 签名已按该
  版本校验；升级 SDK 时这里可能需要微调。
- **原文落库**：解析全文进 `kb_document.raw_text`，切片进 `kb_document_chunk.content`；删除文档会
  同时清 Milvus 向量、MinIO 对象与切片记录。
- **扫描件 PDF** 没有文本层，需要接 OCR（可在 `DocumentParserFactory` 增加一个分支调用 OCR 服务）。
- **上线前**：改 `INIT_ADMIN_PASSWORD`、收敛 CORS 白名单、确认 prod profile 已关闭 Swagger。
