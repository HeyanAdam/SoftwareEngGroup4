# 01 公共层 common

路径：`backend/src/main/java/com/group4/kaoyan/common/`

```
common/
├── R.java                         统一响应体（含分页包装 PageData）
├── BizException.java              业务异常（code 直接作为响应 code）
├── GlobalExceptionHandler.java    全局异常处理
└── config/
    ├── AiProperties.java          所有可配置项（@ConfigurationProperties(prefix="kaoyan")）
    └── WebConfig.java             CORS / Sa-Token 拦截器 / MyBatis 分页 / 文档线程池
```

## R —— 统一响应

路径：`backend/src/main/java/com/group4/kaoyan/common/R.java`

```java
public class R<T> {
    private int code;      // 0 = 成功
    private String message;
    private T data;

    public static <T> R<T> ok(T data) { ... }
    public static <T> R<T> fail(int code, String message) { ... }

    public static class PageData<T> {   // total + records
        private long total;
        private List<T> records;
    }
}
```

约定：**成功 `code=0`**，失败用 HTTP 业务码语义（400 参数、401 未登录、403 无权限、404 不存在、500 服务端）。
前端只认 `code`，不要依赖 HTTP 状态码（当前除 SSE 外都是 200 + body 里的 code）。

## BizException —— 业务异常

路径：`backend/src/main/java/com/group4/kaoyan/common/BizException.java`

```java
public class BizException extends RuntimeException {
    private final int code;

    public BizException(String message) { this(400, message); }   // 默认 400
    public BizException(int code, String message) { ... }
}
```

service 层直接 `throw new BizException(403, "只有管理员可以创建知识库")`，由全局异常处理转成 `R.fail(403, ...)`。

## GlobalExceptionHandler —— 异常收敛

路径：`backend/src/main/java/com/group4/kaoyan/common/GlobalExceptionHandler.java`

| 异常 | 转成 |
| --- | --- |
| `BizException` | `R.fail(e.code, e.message)` |
| `MethodArgumentNotValidException` / `BindException` | `R.fail(400, "<字段> <原因>")` |
| `NotLoginException`（Sa-Token） | `R.fail(401, "未登录或登录已失效")` |
| `NotRoleException`（Sa-Token） | `R.fail(403, "缺少角色：xxx")` |
| `NotPermissionException`（Sa-Token） | `R.fail(403, "缺少权限：xxx")` |
| 其它 `Exception` | `R.fail(500, "服务端异常：...")` + 错误日志 |

**鉴权失败不会抛 500**，这是 RBAC 能给出可读错误的关键——见 `03-认证与RBAC.md`。

## AiProperties —— 配置集中地

路径：`backend/src/main/java/com/group4/kaoyan/common/config/AiProperties.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "kaoyan")
public class AiProperties {
    private Llm llm; private Embedding embedding; private Milvus milvus;
    private Storage storage; private Rag rag;

    public static class Llm       { baseUrl; apiKey; model; temperature; maxTokens; timeoutSeconds; }
    public static class Embedding { baseUrl; apiKey; model; dimension; batchSize; timeoutSeconds; }
    public static class Milvus    { host; port; collection; dimension; topK; scoreThreshold; }
    public static class Storage   { endpoint; accessKey; secretKey; bucket; }
    public static class Rag       { chunkSize; chunkOverlap; historyRounds; }
}
```

代码里**不出现任何地址 / 密钥 / 模型名**，全部来自 `application.yml` 的 `kaoyan.*`，再由环境变量覆盖
（详见 `08-数据库与配置.md`）。换模型只改 `LLM_MODEL` 这类变量。

## WebConfig —— 拦截器、跨域、线程池

路径：`backend/src/main/java/com/group4/kaoyan/common/config/WebConfig.java`

### ① 登录拦截（认证入口，不是授权）

```java
registry.addInterceptor(new SaInterceptor(handler -> SaRouter.match("/api/**")
                .notMatch("/api/auth/login", "/api/auth/register")
                .check(r -> StpUtil.checkLogin())))
        .addPathPatterns("/**");
```

- `/api/**` 默认**必须登录**；
- 只放行登录、注册两个接口（`/api/auth/me` 需要登录，因为要读取当前用户）；
- 这里只判断“登录了没有”，**具体能做什么由方法上的 `@SaCheckPermission` 决定**，两者分工见 03 文档。

### ② CORS

`allowedOriginPatterns("*") + allowCredentials(true)`。生产环境要收敛到实际前端域名，
否则任何站点都能带 cookie 打接口（当前用 Bearer token，风险较低，但仍建议改）。

### ③ MyBatis-Plus 分页

```java
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
```

`AdminService.pageUsers` 的 `Page<User>` 靠它生效，否则 `total` 恒为 0。

### ④ 文档处理线程池

```java
@Bean("documentExecutor")
public ThreadPoolTaskExecutor documentExecutor() {
    core 2 / max 4 / queue 100 / CallerRunsPolicy / 前缀 doc-proc-
}
```

`DocumentProcessService.process` 上的 `@Async("documentExecutor")` 用的就是它。
队列满时 `CallerRunsPolicy` 会让调用线程自己跑，保证任务不丢（代价是上传接口会变慢）。

## 改这里会牵连什么

| 改动 | 影响 |
| --- | --- |
| 改 `R` 的结构 | 所有接口的响应体，前端全部要跟着改 |
| 改 `WebConfig` 的 `notMatch` 列表 | 登录拦截范围；漏一个接口就是未授权可访问 |
| 改 `AiProperties` 字段名 | 必须同步 `application.yml` 的 `kaoyan.*` 键，否则启动报绑定失败 |
| 改线程池参数 | 文档并发处理能力，太小会积压、太大可能打爆 Embedding API 限流 |
