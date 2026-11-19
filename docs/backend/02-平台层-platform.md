# 02 平台层 platform（基础能力）

路径：`backend/src/main/java/com/group4/kaoyan/platform/`

```
platform/
├── ai/llm/LlmClient.java             对话模型接口
├── ai/llm/OpenAiLlmClient.java       OpenAI 兼容实现（DeepSeek/通义/Ollama 通用）
├── ai/embedding/EmbeddingClient.java 向量模型接口
├── ai/embedding/OpenAiEmbeddingClient.java
├── ai/vectorstore/VectorStore.java   向量库接口
├── ai/vectorstore/MilvusVectorStore.java
├── storage/StorageService.java       对象存储接口
├── storage/MinioStorageService.java
├── cache/package-info.java           预留（Redis 基础能力）
├── messaging/package-info.java       预留（RabbitMQ 基础能力）
└── realtime/package-info.java        预留（WebSocket 基础能力）
```

设计原则：**platform 不认识任何业务**。向量库里存的只是 `VectorRecord`，它不知道“知识库”是什么；
所以以后把 Milvus 换成 Qdrant、把 MinIO 换成 OSS，只改这一层，`modules` 一行都不用动。

## LlmClient —— 对话模型抽象

路径：`backend/src/main/java/com/group4/kaoyan/platform/ai/llm/LlmClient.java`

```java
public interface LlmClient {
    record Msg(String role, String content) {          // role: system / user / assistant
        public static Msg system(String c) { ... }
        public static Msg user(String c) { ... }
    }
    Flux<String> streamChat(List<Msg> messages);       // 流式：内容增量
    String chat(List<Msg> messages);                   // 非流式
}
```

返回 `Flux<String>` 而不是阻塞读取，是为了让 SSE 直接订阅即可（见 `06-AI问答与SSE-chat.md`）。

### OpenAiLlmClient 实现要点

路径：`backend/src/main/java/com/group4/kaoyan/platform/ai/llm/OpenAiLlmClient.java`

- 用 `WebClient` 打 `POST {LLM_BASE_URL}/chat/completions`，`body.put("stream", true/false)`；
- 流式：逐行解析 SSE，只认 `data:` 开头，取 `choices[0].delta.content`；
  一次响应里可能有多行 `data:`，所以是“按行拆再解析”，不是一个 chunk 一个 JSON；
- 非流式：取 `choices[0].message.content`；
- `model` / `apiKey` / `baseUrl` 全部来自 `AiProperties.Llm`，代码里没有硬编码。

> 兼容性：只要服务端是 OpenAI 协议（DeepSeek、通义千问兼容模式、本地 Ollama 的 `/v1`），换 `LLM_BASE_URL` + `LLM_MODEL` 就能切。

## EmbeddingClient —— 向量模型抽象

路径：`backend/src/main/java/com/group4/kaoyan/platform/ai/embedding/EmbeddingClient.java`

```java
public interface EmbeddingClient {
    List<float[]> embed(List<String> inputs);   // 批量
    float[] embedOne(String input);             // 单条（检索时用）
}
```

- 批量大小由调用方控制：`DocumentProcessService` 按 `EMBEDDING_BATCH_SIZE`（默认 16）切批；
- 返回数量必须与输入数量一致，不一致就直接报错——`DocumentProcessService` 里有断言，
  否则向量和切片会错位，检索出来的内容张冠李戴；
- `EMBEDDING_DIMENSION` 和 Milvus collection 维度必须一致，否则写入直接失败。

## VectorStore —— 向量库抽象

路径：`backend/src/main/java/com/group4/kaoyan/platform/ai/vectorstore/VectorStore.java`

```java
public interface VectorStore {
    record VectorRecord(long id, float[] vector, long chunkId, long docId,
                        long kbId, long userId, String content, String sourceFilename) { }

    record Hit(long chunkId, long docId, long kbId, String content,
               String sourceFilename, float score) { }

    void ensureCollection();
    void upsert(List<VectorRecord> records);
    List<Hit> search(float[] query, String expr, int topK);   // expr = Milvus 布尔表达式
    void deleteByDocId(long docId);
    void deleteByKbId(long kbId);
}
```

`search` 的 `expr` 是**数据隔离的落点**：谁能检索到哪些库，全看这个表达式由谁拼、拼成什么样。
拼接逻辑在 `RetrievalService.buildExpr`（见 `05-知识库与文档流水线.md`），不在这里。

### MilvusVectorStore 实现要点

路径：`backend/src/main/java/com/group4/kaoyan/platform/ai/vectorstore/MilvusVectorStore.java`

collection：`doc_chunk`（名字来自 `MILVUS_COLLECTION`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Int64，主键 | 等于切片 id |
| `vector` | FloatVector(dim) | 维度 = `EMBEDDING_DIMENSION` |
| `chunk_id` | Int64 | `kb_document_chunk.id` |
| `doc_id` | Int64 | 文档 id |
| `kb_id` | Int64 | 知识库 id，检索过滤用 |
| `user_id` | Int64 | 上传者，保留用于审计 |
| `content` | VarChar(4096) | 切片正文，检索命中后直接作为上下文 |
| `source_filename` | VarChar(512) | 原始文件名，管理员引用里显示 |

索引：`HNSW(M=16, efConstruction=200)`，度量 `COSINE`（因为 bge-m3 这类模型输出已归一化）。

```java
// 写入：milvus-sdk-java 2.4.1 的方法名是 withFields，不是 withFieldsData
InsertParam.newBuilder()
    .withCollectionName(cfg.getCollection())
    .withFields(fields)          // List<InsertParam.Field>
    .build();
```

启动时 `ensureCollection()`：不存在就建（含索引）并 `loadCollection`；**连不上 Milvus 只打 `warn`
不阻断启动**——这样没起中间件也能开发接口，代价是后续上传/检索会失败。

## StorageService —— 对象存储抽象

路径：`backend/src/main/java/com/group4/kaoyan/platform/storage/StorageService.java`

```java
public interface StorageService {
    void upload(String objectKey, InputStream in, long size, String contentType);
    InputStream download(String objectKey);
    void delete(String objectKey);
}
```

### MinioStorageService 实现要点

路径：`backend/src/main/java/com/group4/kaoyan/platform/storage/MinioStorageService.java`

- 启动时 `ensureBucket()`：bucket 不存在自动创建，日志打“已创建 MinIO bucket：xxx”；
- 同样**失败只 warn**，不影响启动；
- `objectKey` 由业务侧生成，格式 `<userId>/<kbId>/<uuid>.<ext>`（见 `KnowledgeService.upload`）；
- `delete` 失败只 warn（对象可能已被删），不阻断主流程。

原始文件在 MinIO，解析后的全文在 `kb_document.raw_text`，两者是**双份**：
MinIO 保留原件可下载，`raw_text` 让检索/重解析不依赖对象存储。

## 三个 package-info 占位

路径：
`platform/cache/package-info.java`、`platform/messaging/package-info.java`、`platform/realtime/package-info.java`

目前只是占位，说明未来职责：

| 包 | 计划放什么 |
| --- | --- |
| `cache` | Redis 封装（会话缓存、热点知识库缓存、限流） |
| `messaging` | RabbitMQ 封装（把文档流水线从本地线程池换成消息队列） |
| `realtime` | WebSocket 封装（站内通知、多端同步） |

现在文档处理用的是 `WebConfig` 里的 `documentExecutor` 线程池（单机、进程内），
换成 MQ 时替换 `DocumentProcessService.process` 的触发方式即可，业务代码不变。

## 改这里会牵连什么

| 改动 | 影响 |
| --- | --- |
| 换 embedding 模型 / 维度 | 必须删掉旧 Milvus collection（维度不一致会写入失败），并重跑所有文档的向量化 |
| 改 `VectorStore.Hit` 字段 | `RetrievalService`、`RagChatService.toRefs` 都要改 |
| 换向量库实现 | 只需新写一个 `VectorStore` 实现并设为 Bean；但 `expr` 语法是 Milvus 方言，要重新映射过滤条件 |
| 换对象存储 | 实现 `StorageService` 即可，`objectKey` 规则不变 |
