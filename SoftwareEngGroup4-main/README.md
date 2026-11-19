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

## 启动方式

1. 启动 Docker Desktop
2. 进入 deploy，复制 .env.example 为 .env
3. 执行 docker compose up -d
4. 进入 backend，执行 mvn spring-boot:run；Flyway 会自动建表或升级数据库
5. 进入 frontend，执行 npm install 和 npm run dev

## 访问地址

- 前端：http://localhost:5173
- Swagger：http://localhost:8080/swagger-ui/index.html
- 健康检查：http://localhost:8080/api/v1/health/ready
