# 实时连接层（成员 B）· PR B-1：WebSocket 基础与票据鉴权

> 分支：`feat/realtime-foundation`
> 基线：`main`（本分支从最新 main 切出，比指南里的 `f8435e9` 更新）
> 前置：无，可独立合并
> 范围：只新增连接层，不改聊天业务、不改前端、不覆盖现有 MVC 鉴权配置

## 一、这次交付了什么

| 能力 | 落点 |
| --- | --- |
| WebSocket 依赖与端点注册 | `backend/pom.xml`、`platform/realtime/config/RealtimeWebSocketConfig.java` |
| 一次性票据签发（Redis，60 秒） | `platform/realtime/ticket/RealtimeTicketService.java` |
| 票据接口 `POST /api/v1/chat/ws-ticket` | `modules/chat/realtime/WsTicketController.java` |
| 握手鉴权（票据 / Origin / 账号状态 / `chat:send`） | `platform/realtime/handshake/RealtimeHandshakeInterceptor.java` |
| 多端连接注册表 | `platform/realtime/session/RealtimeConnectionRegistry.java` |
| 统一消息解析与校验（JSON / 版本 / 大小 / 必填） | `platform/realtime/protocol/RealtimeMessageParser.java` |
| 应用层 ping/pong + 空闲清理（90 秒） | `platform/realtime/handler/RealtimeChatHandler.java`、`heartbeat/RealtimeHeartbeatMonitor.java` |
| 错误码与关闭原因 | `platform/realtime/protocol/RealtimeErrorCode.java` |
| 对外推送端口（可复用） | `platform/realtime/delivery/RealtimeDeliveryPort.java` |
| 业务命令扩展点（留给 B-2） | `platform/realtime/command/RealtimeCommandHandler.java` |

目录按指南第五节铺开：`config / ticket / handshake / session / protocol / heartbeat / delivery / cluster`；
`cluster` 只在 `package-info` 里占位，跨实例转发留到多副本部署时再做。

## 二、协议（B 和 C 之间的契约）

连接：`ws://<host>/ws/chat?ticket=<一次性票据>`

票据申请：

```
POST /api/v1/chat/ws-ticket
Authorization: Bearer <登录 Token>
权限：chat:send
```

返回（沿用项目统一的 `R` 包装，`data` 为协议三字段）：

```json
{
  "code": 0,
  "message": "success",
  "data": { "ticket": "rt_xxxxxxxx", "expiresIn": 60, "wsUrl": "/ws/chat" }
}
```

客户端消息：

```json
{ "version": 1, "type": "ping", "requestId": "ping-uuid" }
```

```json
{
  "version": 1,
  "type": "chat.send",
  "requestId": "request-uuid",
  "clientMessageId": "client-message-uuid",
  "data": { "conversationId": null, "kbId": null, "content": "这道题为什么选 B？" }
}
```

服务端应答：

```json
{ "version": 1, "type": "pong", "requestId": "ping-uuid", "data": {} }
```

```json
{ "version": 1, "type": "error", "requestId": "request-uuid", "data": { "code": 4404, "reason": "暂不支持的消息类型：chat.send", "retryable": false } }
```

> B-1 阶段没有任何 `RealtimeCommandHandler` 实现，所以 `chat.send` 会收到 4404，这是预期行为；
> B-2 加上实现类后自动接管，连接层代码不用改。

### 错误码

| code | 含义 | retryable |
| --- | --- | --- |
| 4400 | 消息格式不合法（JSON、字段、类型） | `false` |
| 4401 | 票据无效 / 过期 / 已用掉 | `false` |
| 4403 | 无通道权限、账号被禁用、Origin 不合法 | `false` |
| 4404 | 暂不支持的消息类型 | `false` |
| 4408 | 空闲超时（服务端主动关闭） | `true` |
| 4413 | 消息体超过 32 KB | `false` |
| 4426 | 协议版本不支持 | `false` |
| 4429 | 连接数超限 / 请求过于频繁 | `true` |
| 4500 | 服务端内部错误 | `true` |

错误数据固定为：

```json
{
  "code": 4400,
  "reason": "消息格式不合法",
  "retryable": false
}
```

握手失败时 WebSocket 还没建立，返回 HTTP 401/403，并在响应头 `X-Realtime-Error` 里带错误码名。

## 三、鉴权流程（答辩可讲的部分）

1. 浏览器调 `POST /api/v1/chat/ws-ticket`（带登录 Token），服务端签一张 60 秒票据；
2. 票据存 Redis：`realtime:ticket:{ticket}`，value 为 `{userId, allowedChannels, issuedAt, expiresAt}`；
3. 客户端用 `?ticket=xxx` 握手，拦截器用 **GETDEL** 原子取出并删除（并发握手也不可能拿到同一张票）；
4. 依次校验 Origin 白名单 → 账号状态（复用 `AccountStatusGuard`）→ `chat:send`（复用 `RbacSnapshotCache` 快照）；
5. 校验通过后 attributes 里只放 `userId / channels / 连接时间`，票据原文不写日志、不进会话属性。

Token 不进 URL，是这套设计存在的核心理由：URL 会进浏览器历史、Nginx access log 和 Referer。

## 四、配置项（都有默认值，不配也能跑）

```yaml
kaoyan:
  realtime:
    ws-path: /ws/chat
    ticket-ttl: 60s
    heartbeat-interval: 30s
    idle-timeout: 90s
    max-message-bytes: 32768
    max-text-length: 4000
    max-connections-per-user: 6
    allowed-origins:
      - http://localhost:5173
      - http://127.0.0.1:5173
    idle-sweep-interval-ms: 10000
```

上线时把 `allowed-origins` 换成真实前端域名；支持 `http://localhost:*` 这种端口通配。

## 五、本地联调步骤（PR 里要附的连接说明）

1. 起基础设施 + 后端（IDEA 里选 `一键启动`，或 `mvn spring-boot:run`，Redis 必须可用）；
2. 登录拿 Token（现有登录接口，`Authorization: Bearer <token>`）；
3. 申请票据：

```powershell
curl.exe -X POST http://localhost:8080/api/v1/chat/ws-ticket `
  -H "Authorization: Bearer <token>"
```

4. 建连（任选一种）：

```powershell
# wscat：npm i -g wscat
wscat -c "ws://localhost:8080/ws/chat?ticket=rt_xxxx"
```

或在前端页面（`http://localhost:5173`）控制台里执行（浏览器会自动带 Origin）：

```javascript
const ws = new WebSocket("ws://localhost:8080/ws/chat?ticket=rt_xxxx");
ws.onopen = () => ws.send(JSON.stringify({ version: 1, type: "ping", requestId: "p1" }));
ws.onmessage = (e) => console.log(e.data);
// 预期：{"version":1,"type":"pong","requestId":"p1","data":{}}
```

5. 验证一次性：同一张票据再连一次，握手直接 401。

## 六、自动化测试

```powershell
cd backend
mvn -Dtest="*Realtime*Test" test   # 本 PR 的 34 个用例
mvn test                           # 全量回归，171 个用例
```

| 用例类 | 覆盖验收点 |
| --- | --- |
| `RealtimeTicketServiceTest` | 60 秒 TTL、GETDEL 一次性、过期/坏数据判无效 |
| `RealtimeHandshakeInterceptorTest` | 有效票据放行；缺失/重复 401；Origin、权限、账号状态 403 |
| `RealtimeConnectionRegistryTest` | 多标签页共存、超限拒绝、关闭无残留、兜底清理 |
| `RealtimeMessageParserTest` | JSON、版本、大小、必填字段、超长正文 |
| `RealtimeHeartbeatMonitorTest` | 空闲关闭、活跃不误杀、死连接清理 |
| `RealtimeDeliveryServiceTest` | 多端广播、定向推送、写失败即摘除 |

## 七、B-1 验收清单对照

- [x] 票据 60 秒过期且只能消费一次
- [x] 握手校验登录、账号状态和 `chat:send`
- [x] 同一用户多标签页均可在线（默认上限 6，可配）
- [x] 心跳与空闲关闭正常
- [x] JSON、版本、大小、频率（错误码与扩展点已留）均有校验
- [ ] `chat.send/ack/delta/done` 最小链路 —— B-2 落地
- [x] 越权连接（无权限/禁用/错 Origin）无法建立
- [x] 连接层可被其他业务复用（`RealtimeDeliveryPort` + `RealtimeCommandHandler`）
- [x] `mvn test` 通过

## 八、PR 描述草案

```
标题：feat(realtime): WebSocket 基础与票据鉴权（B-1）

改动范围：仅新增 platform/realtime 连接层 + modules/chat/realtime 票据接口 + websocket 依赖
协议变化：新增 /ws/chat、POST /api/v1/chat/ws-ticket、ping/pong、error 帧；chat.send 暂回 4404（B-2 接管）
鉴权方式：一次性票据（Redis GETDEL，60s）+ Origin 白名单 + 账号状态 + chat:send
测试方法：mvn -Dtest="*Realtime*Test" test（34 例）；mvn test 全量 171 例
连接示例：见 docs/00_项目管理/实时连接层_B_WebSocket基础与票据鉴权.md 第五节
前置 PR：无
```
