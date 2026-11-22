# 大屏验收证据目录（admin-dashboard）

本目录存放《管理员运营大屏验收记录》第 5 节的真实环境证据。命名规则与对应验收项如下：

| 文件名 | 内容 | 对应步骤 |
|---|---|---|
| `01-dashboard-1920x1080.png` | 1920×1080 大屏正常状态截图 | E7 / E10 |
| `02-health-ready.json` | `GET /api/v1/health/ready` 输出 | E7 |
| `03-rabbitmqctl-queues.txt` | `rabbitmqctl list_queues` 输出，与页面队列数字对照 | E7 |
| `04-redis-down.png` | `docker stop kaoyan-redis` 后的大屏截图（Redis 红、业务数据仍展示） | E7 |
| `05-rabbitmq-down.png` | `docker stop kaoyan-rabbitmq` 后的大屏截图 | E7 |
| `06-recovered.png` | 中间件恢复后自动回绿截图 | E7 |
| `07-days-7-14-30.png` | 7/14/30 天切换截图（可拼一张） | E7 |
| `08-sql-results.txt` | KPI 对应 SQL 统计结果 | E7 / E9 |
| `09-redis-key-ttl.txt` | `redis-cli --scan --pattern admin:dashboard:summary:v1:*` 与 `TTL` 输出 | E1 / E9 |
| `10-student-403.json` | 学生越权响应（HTTP 403 + code 10008） | E8 |
| `11-tests-and-build.txt` | `mvn test` 与前端 `npm run build` 输出 | E6 / E10 |

## 采集约定

1. 截图保留浏览器地址栏或时间水印，便于证明是同一环境的连续验收。
2. 文本类证据直接保存命令原始输出，不要手工改写数字。
3. 采集完成后，在本文件末尾追加一行「采集时间 / 执行人 / 环境 commit」，再更新验收记录的状态列。
4. 证据中不得出现邮箱明文、聊天正文、RabbitMQ/Redis 密码或对象存储密钥。

## 当前状态

- 代码与自动化测试证据：见验收记录第 4 节（`mvn test` 122 项通过）。
- 真实环境证据：**待采集**（本机无 Docker 运行环境）。
