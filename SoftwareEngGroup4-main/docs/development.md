# 开发环境启动

## 1. 环境要求

- JDK 17
- Maven 3.9 或兼容版本
- Node.js 22 LTS
- Docker Desktop
- Git

## 2. 获取代码

```powershell
git clone https://github.com/HeyanAdam/SoftwareEngGroup4.git
cd SoftwareEngGroup4
git switch sprint/foundation
```

## 3. 启动基础服务

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

基础服务：

| 服务 | 地址 |
|---|---|
| Nginx | `http://localhost:8088` |
| MySQL | `localhost:3306` |
| Redis | `localhost:6379` |
| RabbitMQ AMQP | `localhost:5672` |
| RabbitMQ 管理页 | `http://localhost:15672` |
| MinIO API | `http://localhost:9000` |
| MinIO 管理页 | `http://localhost:9001` |
| Mailpit SMTP | `localhost:1025` |
| Mailpit 页面 | `http://localhost:8025` |

默认测试账号以 `.env.example` 为准：

```text
MySQL 用户：kaoyan
MySQL 密码：kaoyan123
MySQL 数据库：kaoyan_ai
RabbitMQ 用户：kaoyan
RabbitMQ 密码：kaoyan123
MinIO 用户：minioadmin
MinIO 密码：minioadmin
```

## 4. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端启动后检查：

```text
http://localhost:8080/api/v1/health/live
http://localhost:8080/api/v1/health/ready
http://localhost:8080/swagger-ui/index.html
```

通过 Nginx 访问健康检查：

```text
http://localhost:8088/api/v1/health/live
```

## 5. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:5173
```

## 6. 停止服务

停止容器：

```powershell
cd deploy
docker compose down
```

重新启动：

```powershell
docker compose up -d
```

## 7. 常见检查命令

```powershell
docker compose ps
docker compose logs mysql
docker compose logs redis
docker compose logs rabbitmq
docker compose logs minio
docker compose logs mailpit
docker compose logs nginx
```

