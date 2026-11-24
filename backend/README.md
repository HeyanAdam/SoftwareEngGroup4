# AI 考研 RAG 系统 · 后端

Spring Boot 3 + MyBatis-Plus + Sa-Token(RBAC) + Milvus + MinIO + 模型 API（OpenAI 兼容）。
所有环境相关参数外置，换机器只需改 `.env`。

## 技术栈

| 组件 | 说明 |
| --- | --- |
| Java 17 / Spring Boot 3.2.5 | Web、Validation、WebFlux(WebClient 流式) |
| MyBatis-Plus 3.5.7 | 单表 CRUD 与分页 |
| MySQL 8 | 业务数据 + 文档解析后全文 |
| Redis 7 | Sa-Token 登录会话、验证码、限流、WebSocket 票据、大屏缓存和聊天恢复流 |
| Sa-Token 1.38 | Redis 会话持久化与 RBAC 注解鉴权 |
| Milvus 2.4 | 向量检索（COLLECTION `doc_chunk`，COSINE + HNSW） |
| MinIO | 原始上传文件（PDF/DOCX/TXT） |
| 模型 API | 对话 `/chat/completions`、向量 `/embeddings`，模型名可配 |

## 持久化约定（原生 MyBatis）

新加的持久化代码一律**原生 MyBatis**（Mapper 接口 + XML + 手写 SQL），与现有 MyBatis-Plus 代码共存：

| 约定 | 说明 |
| --- | --- |
| 依赖 | 保留 `mybatis-plus-spring-boot3-starter`（它内含 MyBatis 核心），不换成 `mybatis-spring-boot-starter`，否则两套自动配置冲突 |
| import | 新代码只用 `org.apache.ibatis.*`，禁止 `com.baomidou.mybatisplus.*` |
| 配置入口 | `mybatis-plus.*` 就是 MyBatis 配置入口，不要再加 `mybatis.*`；XML 由 `mapper-locations: classpath*:mapper/**/*.xml` 加载 |
| Mapper 位置 | 接口放 `<模块>/mapper`，XML 放 `resources/mapper/<模块>/<Entity>Mapper.xml`，`namespace` = 接口全限定名；接口方法与 XML 必须成对 |
| 实体 | 不继承 MP 基类、不加 `@TableName/@TableId/@TableField`；实现 `TimedEntity`，时间戳由 `EntityTimestamps` 显式填充 |
| JSON 列 | `JsonTypeHandler`（全局注册）或 XML 里显式 `typeHandler`；`List<String>` 用 `StringListTypeHandler` 显式指定 |
| 分页 | 不引 PageHelper：`PageQuery` 算 offset，XML 写 `LIMIT #{offset}, #{size}` + 独立 count，pageSize 上限 100 |
| 逻辑删除 / 乐观锁 | 手写：查询带 `deleted = 0`，删除走 `UPDATE ... SET deleted = 1`；乐观锁 `version = version + 1 ... AND version = #{version}`，影响 0 行返回 409 |
| 事务 | service 写方法 `@Transactional(rollbackFor = Exception.class)`；MQ 发送不参与本地事务，统一走 Outbox |

关键基础设施：`common/mybatis`（类型处理器、分页、时间戳）、`platform/idempotency`（接口幂等）、
`platform/messaging`（异步任务 + Outbox + 消费幂等）。

## 数据持久化覆盖的接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / PUT | `/api/v1/user/profile` | 用户画像（`user_profile`），无记录返回空对象 |
| GET / POST | `/api/v1/user/goals` | 备考目标列表 / 新增（新增需 `Idempotency-Key`） |
| PUT / DELETE | `/api/v1/user/goals/{id}` | 更新（乐观锁）/ 逻辑删除 |
| POST | `/api/v1/files` | 小文件直传（先对象后元数据，失败删对象）；同用户同 SHA-256 秒传 |
| POST | `/api/v1/files/upload` | 批量直传 |
| POST | `/api/v1/files/batch` | 批量取元数据 |
| POST | `/api/v1/files/upload-sessions` | 初始化分片会话（需 `Idempotency-Key`，24h 过期） |
| GET | `/api/v1/files/upload-sessions/{id}` | 查询会话进度 |
| PUT | `/api/v1/files/upload-sessions/{id}/parts/{n}` | 上传分片（`X-Part-Hash` 可选） |
| POST | `/api/v1/files/upload-sessions/{id}/complete` | 合并分片并生成文件记录 |
| DELETE | `/api/v1/files/upload-sessions/{id}` | 取消会话 |
| GET | `/api/v1/files` | 我的文件分页列表 |
| GET | `/api/v1/files/{id}/content` | 60 分钟预签名下载地址 |
| DELETE | `/api/v1/files/{id}` | 逻辑删除 + 删对象 |
| GET | `/api/admin/login-logs` | 登录日志排查（管理员） |

其他持久化路径：登录日志（`sys_login_log`，成功/失败都写、独立事务）、操作审计
（`@AuditLog` 切面异步写 `sys_audit_log`）、接口幂等（`@Idempotent` + `Idempotency-Key`，
SUCCESS 回放 / PROCESSING 409 / 内容变化 422）。

## 文档解析链路：Outbox + MQ

上传/重解析不再直接跑 `@Async`，而是事务内写 `sys_async_task` + `sys_message_outbox`：

```
上传 → AsyncTask(PENDING) + Outbox(PENDING) 同一事务
     → OutboxPublisherJob 抢单 → RabbitMQ（publisher-confirm 后 PUBLISHED）
     → DocParseConsumer 消费幂等 → 解析流水线 + 任务 SUCCEEDED 同事务
     → 失败重试 3 次后 reject 进 kaoyan.doc.parse.dlq，任务置 FAILED
```

开关 `kaoyan.mq.doc-parse-enabled`（环境变量 `MQ_DOC_PARSE_ENABLED`）设为 `false` 时回退为本地异步线程，
离线开发不依赖 MQ 也能跑。队列名已改为 `kaoyan.doc.parse.queue`（带死信参数），旧的
`doc_parse_queue` 可以在 broker 上手工删除。

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
└── src/main/resources/
    ├── config/
    │   ├── application.yml             Spring Boot 默认会读 classpath:/config/
    │   ├── application-dev.yml
    │   └── application-prod.yml
    ├── db/migration/                   Flyway 版本化迁移，唯一建表入口
    │   ├── V1__baseline_existing_tables.sql
    │   ├── V2__user_profile_and_goal.sql
    │   └── V3__idempotency_and_audit.sql
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

1. 确认 `deploy/.env` 存在（见上一节），填好两个模型 Key。全项目只维护这一份环境配置。
2. **建表不用手动做**：后端启动时由 Flyway 按版本执行 `src/main/resources/db/migration`。
   `spring.sql.init.mode=never`，旧的 `db/schema.sql` 已移除。
3. 直接启动，无需手工设环境变量：

```powershell
mvn spring-boot:run
```

> IDE 里跑时工作目录一般是 `backend/`，`../deploy/.env` 能被找到；从仓库根目录跑 jar 也能找到
> `./deploy/.env`。两种路径都已配置。

4. 访问 http://localhost:8080/swagger-ui.html（dev 环境开启）。
5. Flyway V6 初始化四个角色（`STUDENT`、`CONTENT_OPERATOR`、`DATA_AUDITOR`、`SYSTEM_ADMIN`）
   和 11 条权限码，`RbacInitializer` 只校验不写库，**不预置任何账号**。管理员是
   “第一个注册的人”：`POST /api/auth/register` 注册成功后，如果库里还没有管理员，这个用户
   直接拿到 `SYSTEM_ADMIN`；之后注册的一律是 `STUDENT`，需要管理员在后台提权。

数据库结构已经覆盖 V1-V6。后续变更必须新增 `V7__...sql` 等迁移文件，禁止修改已经合并的 V1-V6。
空库和已有非空库都通过 `baseline-on-migrate` 从版本 0 接入；`clean-disabled=true` 防止误清库。

## 账号与权限的初始化流程

| 步骤 | 发生什么 | 持久化 |
| --- | --- | --- |
| 首次启动 | Flyway V6 写入四个角色、11 条权限码和默认绑定；Java 启动器只做完整性校验 | 落 MySQL，后续启动不重复写入 |
| 第一次注册 | 库里没有 `SYSTEM_ADMIN` 用户 → 该用户自动成为管理员（只发生一次） | `sys_user_role` 记录，重启不丢 |
| 之后注册 | 一律 `STUDENT`，只有 `chat:send` | 同上 |
| 提权 / 降权 | 管理员调 `PUT /api/admin/users/{id}/roles` 改用户的角色 | 同上 |
| 调权限 | 管理员调 `PUT /api/admin/roles/{id}/permissions` 改角色拥有的权限码 | 同上 |

| 角色 | V6 默认能力 |
| --- | --- |
| `SYSTEM_ADMIN` | 全部权限 |
| `CONTENT_OPERATOR` | 知识库、文档和向量维护 |
| `DATA_AUDITOR` | 用户、角色和知识库只读 |
| `STUDENT` | 仅 `chat:send` |

重启不会被覆盖：RBAC 种子只由 Flyway V6 执行一次；`RbacInitializer` 只检查必填角色、权限和绑定，
不写库，所以控制台里改过的增删权限不会被启动过程还原。

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

| 能力 | SYSTEM_ADMIN | STUDENT（普通用户） |
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

1. **权限码**：`SYSTEM_ADMIN` 拥有全部权限；`CONTENT_OPERATOR` 负责知识库、文档和向量；
   `DATA_AUDITOR` 只读；`STUDENT` 只有 `chat:send`。没有对应权限的请求在 Controller 层直接 403。
2. **服务层**：`KnowledgeService` 的读方法（列表 / 详情 / 文档列表 / 原文）与写方法一律先
   `requireAdmin(...)`，权限码被误配时也不会漏。
3. **检索侧**：普通用户不认 `kbId`（传了也忽略），统一按“公开库 `kb_id` 集合”检索；管理员才按指定
   库或全量检索。库结构不暴露给普通用户。
4. **输出侧**：`references` 事件与落库的 `references_json` 按角色分级——普通用户直接返回空数组
   `[]`（连文档名都不给），管理员才有 `docName / docId / chunkId / score / snippet`。

`STUDENT` 是内置的默认注册角色，初始权限就是 `chat:send`。种子只在 V6 首次执行时写入，
之后完全以数据库为准——管理员在后台加或减权限，重启都不会被还原。

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
