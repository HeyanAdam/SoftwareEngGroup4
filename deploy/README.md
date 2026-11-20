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
# 中间件：MySQL / Redis / RabbitMQ / Mailpit（MinIO / Milvus 默认未启用，见下文）
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
- MySQL 容器不再直接执行建表脚本。后端启动时由 Flyway 执行
  `backend/src/main/resources/db/migration`，空库和已有非空库使用同一套版本记录。
- `.env` 已被 `.gitignore` 忽略，**不要提交**。

## MinIO 说明（默认不启用）

`docker-compose.yml` 里的 `minio` 段是**注释状态**，`docker compose up -d` 不会起它。

**为什么注释**：本机 Docker 的出网请求走了 `docker.internal:3128` 代理，访问 Docker Hub 和国内镜像站（`docker.m.daocloud.io`、`docker.1ms.run`、`hub.rat.dev`）统一返回
`Error response from daemon: error from registry: denied`，镜像拉不下来。这是**网络/代理环境问题**，不是 compose 写错。

**未启用时的影响**：后端启动会打印 `MinIO 初始化失败，文件功能暂不可用` 并自动降级，`/api/file/**` 相关接口不可用；
其余模块（认证、RBAC、审计、知识库元数据、问答）不受影响，服务能正常启动。

**怎么启用**（任选其一）：

1. 修好 Docker 出网（Docker Desktop → Settings → Resources → Proxies，关掉/清空代理，或指向本机可用代理端口如 `http://host.docker.internal:7890`），然后：
   ```powershell
   docker pull minio/minio:latest
   ```
2. 或者从任意渠道拿到 `minio/minio` 镜像文件后 `docker load -i minio.tar`，确认
   `docker images | findstr minio` 能看到该镜像，再执行下面第 3 步。
3. 镜像到位后，取消 `docker-compose.yml` 中 `minio:` 整段注释，执行：
   ```powershell
   docker compose up -d minio
   ```
   后端重启时会自动创建 bucket（`kaoyan-files`，见 `MinioStorageService.ensureBucket`），无需手工建桶。

## Milvus 说明（默认不启用）

`docker-compose.yml` 里没有 Milvus 服务（需要 `etcd` + `minio` + `milvus-standalone` 三件套）。
未启用时后端会打印 `Milvus 初始化失败，向量检索暂不可用`，问答接口仍能返回，但**不带知识库检索结果**。

另外 `LLM_API_KEY` / `EMBEDDING_API_KEY` 目前是占位符（`sk-替换成你的Key`），
即使把 Milvus 起来，embedding 调用也会 401 —— **先配好真实 Key 再考虑启用 Milvus**，
并且集合 `doc_chunk` 的维度必须与 `EMBEDDING_DIMENSION`（默认 1024）一致，换模型后要删集合重建。
