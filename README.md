# 考研 AI 导学系统

## 技术栈

- Java 17
- Spring Boot
- MyBatis-Plus
- MySQL
- Redis
- RabbitMQ
- MinIO
- Vue 3
- Docker Compose

## 项目目录

- backend：后端代码
- frontend：前端代码
- deploy：Docker 和 Nginx 配置
- docs：设计文档

## 一键启动（IntelliJ IDEA）

前置：Docker Desktop 已启动、JDK 17 与 Maven 已配置（IDEA 自带 Maven 即可），前端首次会自动 `npm install`。

clone 后直接用 IDEA 打开仓库根目录（Maven 项目会自动导入 backend），然后在右上角运行配置下拉里选中：

```text
一键启动      <-- 选这个，点绿色三角
```

点一次即可同时拉起三个任务：`基础设施: 容器启动`（MySQL / Redis / RabbitMQ / Mailpit）→ `前端: 依赖安装` → `前端: 开发服务`（Vite）→ `后端: 应用主类启动`（dev profile，Flyway 自动建表）。Run 面板里会出现三个并行标签页，日志互不干扰，停哪个就点对应标签页的停止按钮。

首次启动如果后端比 MySQL 先就绪而报连接失败，再点一次运行即可（容器此时已经健康）。只想单独启动某一块时，直接选对应的 `基础设施:` / `后端:` / `前端:` 配置，例如 `基础设施: 容器启动`、`后端: 应用主类启动`、`前端: 开发服务`。

## 手工启动（排查问题时用）

1. 启动 Docker Desktop
2. 进入 deploy，复制 .env.example 为 .env
3. 执行 docker compose up -d
4. 进入 backend，执行 mvn spring-boot:run；Flyway 会自动建表或升级数据库
5. 进入 frontend，执行 npm install 和 npm run dev

## IDEA 共享配置说明

仓库内置共享运行配置（`.idea/runConfigurations`），显示名全为中文：

| 运行配置 | 作用 |
| --- | --- |
| `一键启动` | 一次拉起基础设施 + 前端 + 后端 |
| `基础设施: 容器启动` | `docker compose up`（MySQL / Redis / RabbitMQ / Mailpit） |
| `后端: 应用主类启动` | 直接跑 `KaoyanApplication`（dev profile） |
| `后端: Maven 启动` | `mvn spring-boot:run` |
| `前端: 依赖安装` | `npm install` |
| `前端: 开发服务` | `npm run dev`（启动前自动先装依赖） |

共享入库：`misc.xml`、`modules.xml`、`vcs.xml`、`encodings.xml`、`compiler.xml`、`runConfigurations/*.xml`、`frontend.iml`。
个人/机器相关一律忽略：`workspace.xml`、`tasks.xml`、`usage.statistics.xml`、`shelf/`、`jarRepositories.xml`、`dictionaries/`、`dataSources*`、`httpRequests/`，以及 IDEA 导入 Maven 时自动生成的 `backend/*.iml`。

## 访问地址

- 前端：http://localhost:5173
- Swagger：http://localhost:8080/swagger-ui/index.html
- 健康检查：http://localhost:8080/api/v1/health/ready

## 真实邮箱验证码

默认使用本地 Mailpit，只用于开发调试，不会把邮件投递到真实邮箱。需要向真实邮箱发送验证码时，可使用 Brevo、SMTP2GO 或 QQ 邮箱 SMTP：

```powershell
pwsh tools/start-backend.ps1
```

首次运行会引导选择服务商、填写 SMTP 账号和授权码，并生成不入库的 `deploy/mail.env`。之后 IntelliJ 或命令行启动都会读取该配置。已配置过时，使用 `-Reconfigure` 重新选择服务商。

QQ 邮箱填写的是 SMTP 授权码，不是 QQ 登录密码；Brevo、SMTP2GO 需先在服务商控制台启用 SMTP 并生成凭据。免费额度以服务商当前控制台为准。
