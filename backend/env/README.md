# env/ 目录说明

这里的文件**不是**必需的。全项目只有一份环境配置，在仓库根目录：

```
deploy/.env.example   → 复制成 deploy/.env 使用
```

后端启动会自动读 `deploy/.env`（`application.yml` 里配了 `spring.config.import`，
本地 IDE、`mvn spring-boot:run`、`java -jar` 都生效）。

| 文件 | 用途 |
| --- | --- |
| `env/.env.example` | 可选的**个人覆盖**模板；复制成 `env/.env`，只写要覆盖的键（如自己的 `LLM_API_KEY`） |

覆盖优先级（后者覆盖前者）：`deploy/.env` → `backend/env/.env` → 系统环境变量。

`env/.env` 已被 `.gitignore` 忽略，**不要提交**。
