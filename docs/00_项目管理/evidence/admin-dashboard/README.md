# 大屏验收证据目录（admin-dashboard）

本目录存放《管理员运营大屏验收记录》第 5 节的真实环境证据。实际文件名与对应验收项如下：

| 文件名 | 内容 | 对应步骤 |
|---|---|---|
| `01-dashboard-normal.png` | 正常态大屏截图（7 天，829×870 视口） | E7 |
| `02-summary-normal.json` | `GET /api/v1/admin/dashboard/summary?days=7` 响应原文 | E7 |
| `03-rabbitmqctl-queues.txt` | `rabbitmqctl list_queues` 输出，与页面队列数字对照 | E7 |
| `04-redis-down.png` | 停 Redis 后的大屏截图（Redis 红、异常服务提示、业务卡片仍展示） | E7 |
| `05-rabbitmq-down.png` | 停 RabbitMQ 后的大屏截图（Rabbit DOWN、队列归 0、KPI 仍展示） | E7 |
| `06a-redis-recovered-ui.png` | Redis 恢复后页面自动回绿 | E7 |
| `06-rabbitmq-recovered-ui.png` | RabbitMQ 恢复后页面自动回绿 | E7 |
| `07a-days-14.png` / `07b-days-30.png` | 14 天 / 30 天趋势切换截图 | E7 |
| `08-fullscreen-7d.png` | 全屏模式（1707×1068）大屏截图，替代原生 1920×1080 | E7 |
| `09-redis-key-ttl.txt` | 缓存 key 与 `TTL` 输出 | E1 / E9 |
| `10-student-403.json` | 学生越权响应（HTTP 403 + code 10008） | E8 |
| `11-perf.txt` | 冷启动 / 命中 / 连续 30 次刷新耗时 + DB 侧耗时结论 | E9 |
| `12-auth-matrix.json` | 无 token / 学生 / DATA_AUDITOR / SYSTEM_ADMIN 四组权限矩阵 | E8 |
| `13-redis-down-summary.json` | Redis 停机时接口响应（`redis=DOWN`） | E7 |
| `14-rabbitmq-down-summary.json` | RabbitMQ 停机时接口响应（`rabbitmq=DOWN`、队列归 0） | E7 |
| `15-redis-recovered.json` | Redis 恢复后接口响应 | E7 |
| `16-rabbitmq-recovered.json` | RabbitMQ 恢复后接口响应 | E7 |
| `17-outbox-index.txt` | `SHOW INDEX FROM sys_message_outbox` | E9 |
| `18-outbox-explain.txt` | 两条指标 SQL 的 `EXPLAIN` | E9 |
| `20-outbox-profile.txt` | `SET profiling=1` 得到的 SQL 服务端耗时 | E9 |
| `21-unauth-redirect-login.png` | 未登录直访 `/admin/dashboard` 跳 `/admin/login?redirect=...` | E7 |

## 采集约定

1. 截图保留浏览器地址栏或时间水印，便于证明是同一环境的连续验收。
2. 文本类证据直接保存命令原始输出，不要手工改写数字。
3. 采集完成后，在本文件末尾追加一行「采集时间 / 执行人 / 环境 commit」，再更新验收记录的状态列。
4. 证据中不得出现邮箱明文、聊天正文、RabbitMQ/Redis 密码或对象存储密钥。

## 当前状态

- 代码与自动化测试证据：见验收记录第 4 节（`mvn test` 122 项通过）。
- 真实环境证据：**已采集**（2026-11-22，Windows 本地 + Docker Desktop，commit `07452fd`）。
- 未做原生 1920×1080 截图：本机显示器分辨率上限，全屏模式实得 1707×1068，已在验收记录第 5.1 节注明。
