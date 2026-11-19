# 后端代码说明（分模块）

本目录按“模块 → 文件 → 职责 → 关键代码”的方式说明 `backend/` 的实现，所有路径都相对仓库根目录。
代码以 **Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Sa-Token 1.38.0** 为准。

## 文档索引

| 文档 | 内容 | 主要路径 |
| --- | --- | --- |
| [01-公共层-common.md](./01-公共层-common.md) | 统一响应、异常、配置绑定、Web 配置、线程池 | `backend/src/main/java/com/group4/kaoyan/common/` |
| [02-平台层-platform.md](./02-平台层-platform.md) | LLM / Embedding 客户端、Milvus 向量库、MinIO 存储 | `backend/src/main/java/com/group4/kaoyan/platform/` |
| [03-认证与RBAC.md](./03-认证与RBAC.md) | **登录、授权、鉴权全链路**（重点） | `backend/src/main/java/com/group4/kaoyan/modules/auth/` |
| [04-用户模块-user.md](./04-用户模块-user.md) | 本人资料、改密 | `backend/src/main/java/com/group4/kaoyan/modules/user/` |
| [05-知识库与文档流水线.md](./05-知识库与文档流水线.md) | 建库、上传、解析、切片、向量化、检索 | `backend/src/main/java/com/group4/kaoyan/modules/knowledge/` |
| [06-AI问答与SSE-chat.md](./06-AI问答与SSE-chat.md) | 流式问答、会话历史、引用分级 | `backend/src/main/java/com/group4/kaoyan/modules/chat/` |
| [07-后台管理-admin.md](./07-后台管理-admin.md) | 用户/角色/权限维护、向量重建 | `backend/src/main/java/com/group4/kaoyan/modules/admin/` |
| [08-数据库与配置.md](./08-数据库与配置.md) | 表结构、配置分层、环境变量、启动顺序 | `backend/src/main/resources/`、`deploy/` |

## 工程目录（当前实际结构）

```
SoftwareEngGroup4/
├── backend/
│   ├── pom.xml                                  Spring Boot 3.2.5 / Java 17
│   ├── docker/Dockerfile                        多阶段构建
│   ├── env/.env.example                         可选覆盖模板
│   ├── src/main/java/com/group4/kaoyan/
│   │   ├── KaoyanApplication.java               启动类（@EnableAsync）
│   │   ├── common/                              统一响应 / 异常 / 配置
│   │   │   ├── R.java  BizException.java  GlobalExceptionHandler.java
│   │   │   └── config/AiProperties.java  config/WebConfig.java
│   │   ├── platform/                            基础能力（不含业务）
│   │   │   ├── ai/llm/        LlmClient + OpenAiLlmClient
│   │   │   ├── ai/embedding/  EmbeddingClient + OpenAiEmbeddingClient
│   │   │   ├── ai/vectorstore/ VectorStore + MilvusVectorStore
│   │   │   ├── storage/       StorageService + MinioStorageService
│   │   │   └── cache|messaging|realtime/package-info.java   预留
│   │   └── modules/                             业务模块
│   │       ├── auth/       登录、RBAC（角色/权限表、StpInterface、启动初始化）
│   │       ├── user/       用户资料（SysUser）
│   │       ├── knowledge/  知识库、文档、解析、切片、检索、Prompt
│   │       ├── chat/       会话与 RAG 问答（SSE）
│   │       ├── admin/      后台管理
│   │       └── studyplan|file/package-info.java  预留
│   └── src/main/resources/
│       ├── config/application.yml / application-dev.yml / application-prod.yml
│       ├── db/schema.sql
│       └── prompts/rag-system.txt
└── deploy/
    ├── .env.example                            全项目唯一环境配置模板
    └── docker-compose.yml                      中间件 + 可选 app profile
```

## 依赖方向（改代码时别搞反）

```
modules  →  platform  →  common
```

- `modules` 之间：只通过 `service` / `entity` 互相调用，不直接访问别人的 mapper。
  例外是 `admin`，它本身就是权限维护入口，会用到 `auth` 的 mapper。
- `platform` 不认识任何业务：`VectorStore` 只接受 `VectorRecord`，不知道“知识库”是什么。
- `common` 不放业务逻辑。

## 一次请求经过的层（以问答为例）

```
POST /api/chat/stream
  → WebConfig 里的 SaInterceptor            （是否登录）
  → ChatController @SaCheckPermission("chat:send")   （是否有权限码）
  → RagChatService                          （检索 / 组 Prompt / 流式生成）
      → RetrievalService                    （EmbeddingClient + VectorStore）
      → PromptTemplateLoader
      → LlmClient.streamChat
  → ChatService                             （落库会话与消息）
  → SseEmitter 按 meta / references / delta / done 下发
```

## 读代码建议顺序

1. `03-认证与RBAC.md` —— 先搞清“谁能调什么”，后面每个接口的权限才有意义。
2. `05-知识库与文档流水线.md` —— 数据是怎么从文件变成向量片的。
3. `06-AI问答与SSE-chat.md` —— 向量片怎么被用起来。
4. 其余按需。
