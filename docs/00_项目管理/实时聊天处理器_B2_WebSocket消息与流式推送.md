# 实时聊天处理器（成员 B）· PR B-2

> 分支：`feat/realtime-chat-handler`
>
> 基线：`main@594bd00`（A-1、B-1 已合并）
>
> 前置：A-1 `feat/chat-stream-core`、B-1 `feat/realtime-foundation`

## 一、本次交付

| 能力 | 实现 |
| --- | --- |
| `chat.send` 处理 | `ChatRealtimeCommandHandler` |
| 每次消息鉴权 | 账号状态、角色快照、`chat:send` 权限重新校验 |
| 会话归属 | 交给 `ChatService#getOrCreate` 与 `ChatStreamService` 统一校验 |
| ACK | 先回发起连接，包含会话、流和客户端消息 ID |
| 流式事件 | 订阅 A-1 的 `Flux<ChatStreamEvent>` |
| 多端同步 | 通过 `RealtimeDeliveryPort#pushToUser` 广播 |
| 发送频率 | Redis 固定窗口计数 |
| 生成并发 | 用户级和连接级同时限制 |
| 异步执行 | `boundedElastic`，不阻塞 WebSocket IO 线程 |

## 二、处理流程

```text
WebSocket 收到 chat.send
  → 协议校验
  → 账号状态与 chat:send 权限
  → Redis 频率限制
  → 用户/连接并发限制
  → 创建或校验会话
  → 发送 ack
  → 订阅 ChatStreamService.stream(...)
  → 广播 meta/references/delta/done/error
  → 流结束后释放并发计数
```

`ack` 只发给发起消息的连接；`meta/references/delta/done` 广播给该用户全部在线连接。一个标签页关闭不会取消服务端的全局聊天流，其他标签页仍会收到后续事件。

## 三、协议

请求：

```json
{
  "version": 1,
  "type": "chat.send",
  "requestId": "request-uuid",
  "clientMessageId": "client-message-uuid",
  "data": {
    "conversationId": 10,
    "kbId": null,
    "content": "这道题为什么选 B？"
  }
}
```

`clientMessageId` 对 `chat.send` 为必填字段；新建会话时 `conversationId` 可为 `null`。

ACK：

```json
{
  "version": 1,
  "type": "ack",
  "requestId": "request-uuid",
  "data": {
    "conversationId": 10,
    "streamId": "stream-uuid",
    "clientMessageId": "client-message-uuid"
  }
}
```

后续事件保持 A-1 外层字段：

```json
{
  "version": 1,
  "type": "delta",
  "requestId": "request-uuid",
  "streamId": "stream-uuid",
  "sequence": 3,
  "serverTime": "2026-11-22T19:30:00+08:00",
  "data": {
    "content": "选 B 是因为"
  }
}
```

连接层错误保持 B-1 口径：

```json
{
  "version": 1,
  "type": "error",
  "requestId": "request-uuid",
  "data": {
    "code": 4429,
    "reason": "聊天请求过于频繁",
    "retryable": true
  }
}
```

区分方式：带 `streamId` 的 `error` 属于 A-1 聊天流；不带 `streamId` 的 `error` 属于连接层。

## 四、配置

```yaml
kaoyan:
  realtime:
    max-connections-per-user: 6
    max-sends-per-window: 6
    send-rate-window: 10s
    max-concurrent-generations-per-user: 2
    max-concurrent-generations-per-connection: 1
```

对应环境变量：

```text
REALTIME_MAX_CONNECTIONS_PER_USER
REALTIME_MAX_SENDS_PER_WINDOW
REALTIME_SEND_RATE_WINDOW
REALTIME_MAX_CONCURRENT_PER_USER
REALTIME_MAX_CONCURRENT_PER_CONNECTION
```

## 五、测试

```powershell
cd backend
mvn "-Dtest=ChatRealtimeCommandHandlerTest,RealtimeGenerationTrackerTest,RealtimeChatRateLimiterTest,RealtimeMessageParserTest,RealtimeMessageWriterTest" test
mvn test
```

覆盖：

- ACK 只回发起连接。
- `meta/delta` 通过用户级推送端口广播。
- 超频请求在调用 AI 核心前被拒绝。
- 禁用账号不能发送。
- 单连接和单用户并发上限。
- `chat.send` 必填 `clientMessageId`。
- 流事件完整保留 `streamId/sequence/serverTime`。
- B-1 既有票据、握手、心跳和连接注册测试继续通过。

## 六、当前边界

- B-2 在应用进程存活期间保证断开的浏览器标签页不会中断 AI 流。
- 服务实例重启后的任务恢复由 A-2 的 RabbitMQ、Outbox 和 Redis Stream 负责。
- 前端重连补帧由 C-2 使用 `streamId + sequence` 完成。
