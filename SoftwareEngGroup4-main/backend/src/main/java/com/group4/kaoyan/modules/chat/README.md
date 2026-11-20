# AI 聊天模块（modules/chat）

负责人：E

## 后续接口
- `GET  /api/v1/ai-chat/sessions` 会话列表
- `POST /api/v1/ai-chat/sessions` 新建会话
- `POST /api/v1/ai-chat/sessions/{id}/messages` 发送问题（异步返回）
- `WS   /ws/chat` AI 流式回答推送

## 消息队列规划
| 交换机 / 队列 | 路由键 | 用途 |
|---|---|---|
| `kaoyan.direct` → `ai.answer.queue` | `ai.answer` | AI 回答任务分发 |
| `kaoyan.direct` → `ai.retry.queue` | `ai.retry` | 失败重试（延迟重投） |
| `kaoyan.direct` → `ai.dead-letter.queue` | `ai.dead` | 超过重试次数进入死信，人工排查 |

> 当前 foundation 阶段已落地的是 `kaoyan.direct` + `foundation.test.queue`（收发闭环验证）
> 与 `doc_parse_queue`（文档解析任务）。上表为后续聊天链路规划。

## 主要数据表
- `chat_conversation` 会话
- `chat_message` 会话消息
- `async_job` 异步任务记录（后续新增）

## 模块依赖
- 认证模块：识别当前登录用户（Sa-Token）
- MySQL：持久化会话与消息
- RabbitMQ：分发 AI 异步回答任务，保证长耗时请求不阻塞 HTTP 线程
- WebSocket：向前端实时推送 AI 流式回答
