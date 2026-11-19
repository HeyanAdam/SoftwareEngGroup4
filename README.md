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
All: 一键启动      <-- 选这个，点绿色三角
```

点一次即可同时拉起：`Infra: docker compose up`（MySQL / Redis / RabbitMQ / Mailpit）→ `Frontend: npm install` → `Frontend: npm dev`（Vite）→ `Backend: KaoyanApplication`（dev profile，Flyway 自动建表）。Run 面板里会出现三个并行标签页，日志互不干扰，停哪个就点对应标签页的停止按钮。

首次启动如果后端比 MySQL 先就绪而报连接失败，再点一次运行即可（容器此时已经健康）。只想单独启动某个模块时，直接选对应的 `Infra:` / `Backend:` / `Frontend:` 配置。

## 手工启动（排查问题时用）

1. 启动 Docker Desktop
2. 进入 deploy，复制 .env.example 为 .env
3. 执行 docker compose up -d
4. 进入 backend，执行 mvn spring-boot:run；Flyway 会自动建表或升级数据库
5. 进入 frontend，执行 npm install 和 npm run dev

## IDEA 共享配置说明

仓库内置共享运行配置（`.idea/runConfigurations`）：
`All: 一键启动`、`Infra: docker compose up`、`Backend: KaoyanApplication`、`Backend: mvn spring-boot:run`、`Frontend: npm install`、`Frontend: npm dev`。

共享入库：`misc.xml`、`modules.xml`、`vcs.xml`、`encodings.xml`、`compiler.xml`、`runConfigurations/*.xml`、`frontend.iml`。
个人/机器相关一律忽略：`workspace.xml`、`tasks.xml`、`usage.statistics.xml`、`shelf/`、`jarRepositories.xml`、`dictionaries/`、`dataSources*`、`httpRequests/`，以及 IDEA 导入 Maven 时自动生成的 `backend/*.iml`。

## 访问地址

- 前端：http://localhost:5173
- Swagger：http://localhost:8080/swagger-ui/index.html
- 健康检查：http://localhost:8080/api/v1/health/ready
