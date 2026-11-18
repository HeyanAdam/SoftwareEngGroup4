# deploy/ 基础设施

仓库里**唯一**的环境配置文件就是这里的 `.env`：

```powershell
cd deploy
Copy-Item .env.example .env
```

然后只需要填两个 Key（`LLM_API_KEY`、`EMBEDDING_API_KEY`），其余默认值可直接跑。
后端启动会自动读这份文件，不需要再复制第二份。

## 启动

```powershell
# 中间件：MySQL / Redis / RabbitMQ / MinIO / Milvus(+etcd) / Mailpit / Nginx
docker compose up -d
docker compose ps

# 额外把后端也放进容器（可选）
docker compose --profile app up -d --build
```

| 服务 | 地址 |
| --- | --- |
| Nginx | `http://localhost:8088` |
| MySQL | `localhost:3306`（库 `kaoyan_ai`，用户/密码见 .env） |
| Redis | `localhost:6379` |
| RabbitMQ | `localhost:5672`，管理页 `http://localhost:15672` |
| MinIO | API `http://localhost:9000`，控制台 `http://localhost:9001` |
| Milvus | `localhost:19530`，健康检查 `http://localhost:9091/healthz` |
| Mailpit | SMTP `localhost:1025`，页面 `http://localhost:8025` |
| 后端（`--profile app`） | `http://localhost:8080` |

## 说明

- `etcd` 是 Milvus 的元数据存储，不对外暴露端口，只在 compose 内网用。
- Milvus 的向量数据落在 `minio` 的 bucket 里，卷是 `milvus_data`。
- MySQL 挂了 `../backend/src/main/resources/db` 做初始化；后端自己也有 `spring.sql.init`，
  两条路都是幂等的，坏一条不影响建表。
- `.env` 已被 `.gitignore` 忽略，**不要提交**。
