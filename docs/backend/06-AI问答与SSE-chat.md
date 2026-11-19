# 06 AI 问答与会话 chat（SSE）

路径：`backend/src/main/java/com/group4/kaoyan/modules/chat/`

```
modules/chat/
├── controller/ChatController.java      /api/chat/stream + 会话增删查
├── service/ChatService.java            会话、消息、历史格式化
├── service/RagChatService.java         RAG 主流程 + SSE 下发 + 引用分级
├── entity/ChatConversation.java  ChatMessage.java
└── mapper/ConversationMapper.java  MessageMapper.java
```

---

## 一、主流程

路径：`backend/src/main/java/com/group4/kaoyan/modules/chat/service/RagChatService.java`

```java
public SseEmitter chat(Long userId, Long conversationId, Long kbId, String question) {
    if (question == null || question.isBlank()) throw new BizException("问题不能为空");

    ChatConversation conv = chatService.getOrCreate(userId, conversationId, kbId, question);
    String history = chatService.recentHistory(conv.getId());
    chatService.saveUserMessage(conv, userId, question);        // 先落库用户消息

    SseEmitter emitter = new SseEmitter(0L);                    // 0 = 不超时
    send(emitter, "meta", Map.of("conversationId", conv.getId(), "model", model));

    // 普通用户不认 kbId，只能检索公开库；管理员可指定库
    Long filterKbId = StpUtil.hasRole("ADMIN") ? conv.getKbId() : null;
    List<Retrieved> hits = retrievalService.retrieve(filterKbId, question);
    send(emitter, "references", toRefs(hits));

    String prompt = promptTemplateLoader.render(retrievalService.buildContext(hits), history, question);
    llmClient.streamChat(List.of(Msg.system(prompt), Msg.user(question))).subscribe(
            delta -> { answer.append(delta); send(emitter, "delta", Map.of("content", delta)); },
            error -> finish(..., "生成中断：" + error.getMessage()),
            () -> finish(..., null));
    return emitter;
}
```

顺序很重要：

1. **先发 `meta`**，前端拿到 `conversationId` 才能把后续消息挂到正确会话；
2. **再发 `references`**，此时还没开始生成，前端可以先渲染引用区；
3. `delta` 逐字推送；
4. `finish` 里把整段回答 + 引用一起落库，再发 `done`（带 `messageId`）。

落库用的是完整回答（`answer.toString()`），不是增量拼接的前端副本——所以刷新后能复原。

---

## 二、SSE 事件协议

接口：`POST /api/chat/stream`，`Content-Type: text/event-stream`，需要 `chat:send` 权限。

请求体：

```json
{"conversationId": 3, "kbId": 2, "question": "什么是梯度下降"}
```

| event | data | 说明 |
| --- | --- | --- |
| `meta` | `{"conversationId":3,"model":"deepseek-chat"}` | 会话 id + 模型名 |
| `references` | 管理员：`[{"index":1,"docName":"高数.pdf","docId":3,"chunkId":12,"score":0.83,"snippet":"..."}]`<br>普通用户：`[]` | 引用证据 |
| `delta` | `{"content":"逐字增量"}` | 可多次 |
| `done` | `{"messageId":8,"error":null}` | 结束；出错时 `error` 有文案但流程仍结束 |
| `error` | `{"message":"..."}` | 检索/组 Prompt 阶段异常 |

前端用 `fetch` + `ReadableStream` 消费（`EventSource` 只支持 GET，带 body 的问答用不了）：

```js
const res = await fetch('/api/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
  body: JSON.stringify({ question })
});
const reader = res.body.getReader();
// 按 \n\n 切帧，再解析 event: / data: 两行
```

### 为什么普通用户拿到空 `references`

路径：`backend/src/main/java/com/group4/kaoyan/modules/chat/service/RagChatService.java`

```java
private List<Map<String, Object>> toRefs(List<Retrieved> hits) {
    if (!StpUtil.hasRole("ADMIN")) {
        return List.of();          // 普通用户：连文档名都不给
    }
    // 管理员：index / docName / docId / chunkId / score / snippet
}
```

普通用户的业务是“问问题拿答案”，不是“浏览资料库”。所以：

- 检索照常进行，切片内容**作为 Prompt 上下文喂给模型**（模型用了数据）；
- 但下发给前端的 `references` 是空数组，**不暴露文档名、切片 id、相似度**；
- 落库的 `references_json` 也存 `"[]"`（同一份 `toRefs` 的结果），历史消息不会漏。

> 副作用：普通用户看不到“答案出处”。如果产品上需要“来源显示”，就得重新评估这条策略。

---

## 三、会话与历史

路径：`backend/src/main/java/com/group4/kaoyan/modules/chat/service/ChatService.java`

```java
public ChatConversation getOrCreate(Long userId, Long conversationId, Long kbId, String question) {
    if (conversationId != null) {
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null || !userId.equals(c.getUserId()) || c.getDeleted() == 1) {
            throw new BizException(404, "会话不存在");      // 归属校验，防越权读别人会话
        }
        return c;
    }
    ChatConversation c = new ChatConversation();
    c.setUserId(userId);
    c.setKbId(kbId);
    c.setTitle(question.length() > 30 ? question.substring(0, 30) : question);  // 用问题前 30 字当标题
    ...
}
```

`messages(userId, conversationId)`、`delete(userId, conversationId)` 都做了 `userId` 归属校验，
**普通用户之间互相看不到会话**（这是数据隔离，不依赖角色）。

历史进 Prompt 的格式：

```java
public String recentHistory(Long conversationId) {
    int rounds = Math.max(1, props.getRag().getHistoryRounds());   // 默认 6 轮
    ... LIMIT rounds * 2 ...
    sb.append("user".equals(m.getRole()) ? "学生：" : "助教：").append(m.getContent()).append('\n');
}
```

取最近 6 轮（12 条消息），按 id 升序拼成“学生：/助教：”的对话。`HISTORY_ROUNDS` 可配。

会话接口：

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| POST | `/api/chat/stream` | `chat:send` |
| GET | `/api/chat/conversations` | 登录 |
| GET | `/api/chat/conversations/{id}/messages` | 登录（且必须本人） |
| DELETE | `/api/chat/conversations/{id}` | 登录（且必须本人，软删除 `deleted=1`） |

---

## 四、Prompt 模板

路径：`backend/src/main/resources/prompts/rag-system.txt`
加载：`backend/src/main/java/com/group4/kaoyan/modules/knowledge/service/PromptTemplateLoader.java`

```java
template.replace("{context}", context).replace("{history}", history).replace("{question}", question)
```

模板在启动时读一次进内存（`InitializingBean`），改提示词要**重启**，热更新没有做。
三个占位符写错（比如大小写不一致）不会报错，只会原样留在 Prompt 里——改模板后建议实际问一次看效果。

---

## 五、已知限制

| 限制 | 说明 |
| --- | --- |
| SSE 在 Reactor 线程直接 send | `llmClient.streamChat` 的 `subscribe` 回调里调 `emitter.send`，高并发下可能相互阻塞 |
| `SseEmitter` 超时设为 0 | 客户端死连接不会被服务端清理，靠 Tomcat 的异步超时兜底 |
| 没有 token 用量统计 | `chat_message.prompt_tokens / completion_tokens` 字段建了但没填 |
| 生成中断也会落库 | `finish` 在 error 分支也会保存半截回答，`done.error` 里带原因 |
| 会话标题不更新 | 首问决定标题，后续追问不改 |
| `kbId` 只对管理员生效 | 普通用户传了被忽略，不是报错——前端如果做“选库”要注意这点 |
