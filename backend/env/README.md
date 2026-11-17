# env/ 环境文件

这里只放“和部署环境有关、但不应进代码库”的配置，Spring 的 `application*.yml` 在
`src/main/resources/config/`（Spring Boot 默认会读 `classpath:/config/`）。

| 文件 | 用途 |
| --- | --- |
| `.env.example` | 环境变量模板，复制成 `env/.env` 后填真实值 |

使用方式：

```powershell
# 1) 复制模板
Copy-Item env\.env.example env\.env
# 2) 填 DB_PASSWORD / LLM_API_KEY / EMBEDDING_API_KEY 等
# 3) 启动中间件（让 compose 读这个文件）
docker compose --env-file env\.env up -d mysql redis etcd minio milvus
```

IDE 里跑后端时，把 `env\.env` 里的变量加进 Run Configuration 的环境变量，或启动前在终端里
`$env:DB_PASSWORD="..."` 逐个设置。

`env/.env` 已在 `.gitignore` 中被忽略，**不要提交**。
