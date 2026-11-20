# P1 对 P3 前端登录与权限联调的 Review 记录

> 评审人：P1（认证后端）　评审对象：`feat/auth-frontend-integration`（已由 PR #23 合入 `feat/auth-foundation`）
> 评审基准：`origin/feat/auth-foundation`，前端共 22 个文件、+2493 行
> 参考文档：《P1交付给P3_前端登录联调说明》《接口设计文档 V1.1》2.1 / 3.x、《权限表》20 条权限码

## 1. 契约对账

| 契约点 | P1 约定 | P3 实现 | 结论 |
|---|---|---|---|
| 接口前缀 | `/api/v1/auth/**` | `API_BASE_URL='/api/v1'` + `/auth/xxx` | 一致 |
| 认证接口清单 | captcha / verification-codes / register / login / me / logout / password-reset | 7 个全部调用（`me`、`logout` 在 `stores/auth.ts`） | 齐全 |
| 认证头 | `Authorization: Bearer <token>` | 请求拦截器 `Bearer ${token}`，key `sa_token` | 一致 |
| 字段命名 | snake_case | 类型定义 `user_id`、`email_masked`、`has_profile`、`retry_after` 全为 snake_case | 一致 |
| 注册返回 | HTTP 201 + token | 注册后写 token 再拉 `/auth/me` | 一致 |
| 幂等键 | 注册 / 重置密码必带 `Idempotency-Key` | 两处都带，且由表单层复用同一 key（避免重复点击产生第二个账号） | 优于最低要求 |
| 登录方式 | `login_type` = PASSWORD / EMAIL_CODE | 两套请求类型分开定义 | 一致 |

## 2. 错误处理验收

| 场景 | 后端口径 | P3 处理 | 结论 |
|---|---|---|---|
| Token 失效 | 401 + 10001 | 清 token + 派发 `auth:unauthorized` 事件，路由跳 `/login?redirect=...` | 正确 |
| 账号或密码错误 | 401 + 10002 | **刻意不清 token**，仅提示错误 | 正确，且是容易做错的点 |
| 图形验证码错误 | 400 + 10005 | 提示 + 刷新验证码 | 正确 |
| 账号锁定 | 423 + 10003 + Retry-After | 读 `Retry-After` 显示「约 X 后可再试」 | 正确 |
| 限流 | 429 + 10010 + Retry-After | 读 `Retry-After` 起倒计时，并单列 `/429` 错误页 | 正确 |
| 幂等冲突 | 409 + 10011 | 提示重复提交，保留原表单 | 正确 |
| 无权限 | 403 + 10008 | `/403` 错误页 | 正确 |
| 账号禁用 | 403 + 10013 | `/403` 页区分文案 | 见问题 3 |
| 网络异常 | 无响应 | 归一为 `ApiError(code=NETWORK_ERROR)` | 正确 |

另外两点做得好：响应拦截器把「HTTP 状态」与「业务码」分开保存（`httpStatus` / `code`），排障时能直接区分；`X-Request-Id` 全链路透传并在错误页展示，符合可追溯要求。

## 3. 问题清单

### 问题 1（阻断，需 P2 决策）：学员登录后 `/study-plans`、`/files` 必 403

- 现象：`router/index.ts` 给这两个路由配了 `permission: ['study-plan:view','study-plan:create']`、`['file:view','file:upload']`。
- 事实：`RoleConstants.BUILTIN_ROLES` 中 STUDENT 只授 `chat:send`（`RoleConstants` 注释原文：学习计划、题库、文件的 9 条权限码「先给 SYSTEM_ADMIN，学生的授权待评审」）。
- 后果：学生账号登录后点这两个菜单一律跳 `/403`，属于「前后端契约看起来都对、合起来不可用」的典型。
- 需 P2 二选一：① 在角色矩阵中给 STUDENT 授予 `study-plan:view`、`file:view`（业务上学生本来就该能看自己的计划与文件）；② 或前端把这两个路由的守卫改成「登录即可」。

### 问题 2（需统一口径）：管理接口前缀被硬编码成 `/api/admin`

- `request.ts` 里 `ADMIN_API_BASE_URL = '/api/admin'`，注释写明「后端 AdminController 的路由是 `/api/admin/**`，不是 `/api/v1/admin`」。
- 但《权限码清单.csv》登记的是 `/api/v1/admin/**`。两处口径冲突：P2 若把后端改到 `/api/v1/admin`，前端所有管理请求会 404。
- 建议：以《权限码清单》为准，P2 把 `AdminController` 改成 `/api/v1/admin`；P3 同步改常量。**这条不解决，P3 的后台页面注定调不通。**

### 问题 3（需拍板）：前端已依赖 `10013`

- `response.ts`、`ForbiddenPage.vue`、`LoginPage.vue` 都按 `10013` 处理「账号已被禁用」，注释还写了「来源：后端最新联调约定（新增 10013）」。
- 事实：`10013` 是 P1 在实现时自定的（接口文档 2.1 只到 `10012`），《权限表》也没登记。
- 结论：代码本身没问题，但**这个错误码目前没有文档依据**，一旦被改成别的码，前端三处要一起改。建议本轮就把 `10013`（403）写进接口文档 2.1 与《权限码清单》。

### 问题 4（建议，不影响功能）

- `/auth/me`、`/auth/logout` 写在 Pinia store 里而不是 `features/auth/api/auth.api.ts`，与其他 5 个认证接口分散，后续改动容易漏。
- `LoginPage.vue` 单文件 383 行、`RegisterPage.vue` 297 行，表单校验逻辑已抽到 `validators.ts`（好），但提交态/倒计时状态仍耦合在页面里，后续加「微信登录」之类会难维护。

## 4. Mock 与生产安全

`VITE_USE_MOCK` 默认 `false`，只有显式置 `true` 才启用；Mock 层与真实层共用同一套返回结构与 `ApiError`，不会出现「Mock 通过、真机失败」的分叉。这一条值得保留。

## 5. 结论

**P3 的登录链路、错误态页面、Token 与权限处理整体达标，可以进入联调**；但有 1 个阻断项（问题 1）和 2 个口径冲突（问题 2、3）必须在合并到 `main` 之前解决，否则会出现「前端页面齐全但学员进不去、管理页 404、禁用状态文案对不上」的问题。

优先级：问题 2 → 问题 1 → 问题 3 → 问题 4。
