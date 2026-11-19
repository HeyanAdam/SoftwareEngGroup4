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

## 一键启动（推荐）

前置：已安装 Docker Desktop、JDK 17、Maven、Node.js，并加入 PATH。

```bat
:: 仓库根目录双击即可，或者在终端执行
start-all.cmd
```

脚本会自动完成：启动 Docker Desktop（未运行则拉起）→ 检查并生成 `deploy/.env` → `docker compose up -d` → 等待 MySQL/Redis/RabbitMQ 健康 → 新窗口启动后端（dev profile，Flyway 自动建表）→ 新窗口启动前端（缺 `node_modules` 时先 `npm install`）。

常用参数（也可直接调用 PowerShell 脚本）：

```powershell
.\scripts\start-all.ps1                  # 全部启动
.\scripts\start-all.ps1 -SkipFrontend    # 只起基础设施 + 后端
.\scripts\start-all.ps1 -SkipInfra       # 本地已有中间件，只起后端和前端
.\scripts\start-all.ps1 -ForceInstall    # 强制重新 npm install
.\scripts\start-all.ps1 -SkipBackend -SkipFrontend   # 只起基础设施
```

停止：双击 `stop-all.cmd`（等价于 `docker compose stop`）；`stop-all.cmd -Down` 删除容器但保留数据卷；`stop-all.cmd -IncludeApps` 同时结束占用 8080/5173 的后端、前端进程。

## 手工启动（逐步排查时用）

1. 启动 Docker Desktop
2. 进入 deploy，复制 .env.example 为 .env
3. 执行 docker compose up -d
4. 进入 backend，执行 mvn spring-boot:run；Flyway 会自动建表或升级数据库
5. 进入 frontend，执行 npm install 和 npm run dev

## IntelliJ IDEA

仓库内置了共享运行配置（`.idea/runConfigurations`），clone 后直接导入即可看到：
`Infra: docker compose up`、`Backend: KaoyanApplication`、`Backend: mvn spring-boot:run`、`Frontend: npm install`、`Frontend: npm dev`。

个人配置（`workspace.xml`、`jarRepositories.xml`、`tasks.xml` 等）已在 `.gitignore` 中排除，不会被提交。

## 访问地址

- 前端：http://localhost:5173
- Swagger：http://localhost:8080/swagger-ui/index.html
- 健康检查：http://localhost:8080/api/v1/health/ready
