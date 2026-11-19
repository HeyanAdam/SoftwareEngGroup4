# P2 Review：P1 认证后端实现审查结论

> 审查人：P2（RBAC / 接口安全 / 集成）
> 审查对象：P1 交付的认证后端（`feat/auth-backend-authentication`，已合入 `feat/auth-foundation`）
> 审查依据：《登录授权三人分工 P2》任务 9-3 —— P2 负责 Review P1 的登录锁定、Token 吊销、验证码和账号生命周期
> 审查日期：2026-11-19
> 审查方式：静态代码审查（`AuthService`、`LoginAttemptService`、`SessionService`、`CaptchaService`、`EmailCodeService`、`AuthRateLimitService`、`IdempotencyService`、`WebConfig`、`V6__seed_rbac_dictionary.sql`）+ 已有 82 项单元测试结果

---

## 一、总体结论

**有条件通过（Conditional Pass）。**

认证链路的四个审查点在**功能完整性与主流程正确性上全部达标**：登录锁定有窗口与解除机制、密码重置后旧 Token 全量吊销、两套验证码均为一次性消费且有频率与错误次数上限、账号注册不得自动成管理员这一红线守住了。

但发现 **1 个高危遗留、2 个中危设计缺陷、4 个低危改进项**，其中高危项（`/api/kb/**` 被排除在登录校验之外）与中危项（禁用状态只靠踢下线保证）涉及安全边界，建议在最终 PR 前修复；其余可作为版本待办。

---

## 二、逐项审查

### 2.1 登录锁定（PASS，含 1 个中危）

| 检查点 | 结论 | 证据 |
|---|---|---|
| 失败计数窗口 | ✔ 30 分钟窗口，首次失败才设 TTL | `LoginAttemptService.onFailure` |
| 阈值与锁定时长 | ✔ 连续 5 次失败锁定 15 分钟，可配置 | `authProperties.getLoginMaxFailures()` / `getLoginLockDuration()` |
| 锁定判断时机 | ✔ 在密码校验前拦截，锁定期间不消耗验证码 | `AuthService.loginByPassword` 第 81–85 行 |
| 解锁后重新计数 | ✔ 锁定成功后清空失败计数，避免解锁瞬间再次被锁 | `onFailure` 第 61 行 |
| 成功登录清理 | ✔ 清失败计数、锁定标记、IP 计数 | `onSuccess` |
| 防账号枚举 | ✔ 账号不存在与密码错误统一返回 10002 | `AuthService` 第 95、105 行 |

**⚠ 发现 1（中危）：账号不存在时也计入「账号维度」失败次数，可被用于锁定他人账号**

`AuthService` 第 92 行在「账号不存在」分支调用了 `loginAttemptService.onFailure(account, ...)`，而 `onFailure` 对传入的 account 字符串做 `fail:{account}` 计数与锁定。攻击者只要对**他人账号名**连续提交 5 次错误密码（甚至不需要该账号存在与否的准确信息，只要猜中用户名），即可让受害者被锁 15 分钟，属于低成本的定向拒绝服务。

> 建议改法：`onFailure` 拆成两档 —— 「账号存在且密码错误」才累加账号维度计数；「账号不存在」只累加 IP 维度计数。改动点仅 `AuthService` 两处调用 + `LoginAttemptService` 增加一个 `onFailureAccountNotFound` 方法，不影响现有单测断言（`LoginAttemptServiceTest` 7 项）。

### 2.2 Token 吊销（PASS，含 1 个一致性建议）

| 检查点 | 结论 | 证据 |
|---|---|---|
| 密码重置后旧 Token 失效 | ✔ 事务提交后调用 `sessionService.logoutUser(userId)` | `AuthService.resetPassword` 第 250–253 行 |
| 多端并发会话全量吊销 | ✔ 先按 loginId 取全部 token 逐个注销，再按账号维度兜底注销一次 | `SessionService.logoutUser` |
| 只退当前会话 | ✔ `logout()` 只注销当前 token | `SessionService.logoutCurrentSession` |
| 被踢下线后的响应 | ✔ 后续请求触发 `NotLoginException` → HTTP 401 + 10001 | `GlobalExceptionHandler` / `AuthExceptionHandler` |

**💡 一致性建议 2（低危）：吊销入口有两套口径**

P1 用 `SessionService.logoutUser`（逐 token + 账号维度），P2 的 `AdminService.updateStatus` 用 `StpUtil.kickout(userId)`。两者最终都能让旧 Token 失效，但实现不同，将来任一方改动容易漏。建议统一收口到 `SessionService.logoutUser`，禁用用户时也调它（P2 侧配合改）。

### 2.3 验证码（PASS，含 2 个低危）

| 检查点 | 结论 | 证据 |
|---|---|---|
| 图形验证码有效期 | ✔ 5 分钟 TTL，Redis 存答案，响应只回 Base64 图片 | `CaptchaService.generate` |
| 图形验证码错误上限 | ✔ 3 次错误即作废，失败计数与验证码同寿命 | `onFailure` |
| 图形验证码一次性 | ✔ 校验通过立即 `delete` | `verify` 第 68 行 |
| 邮箱验证码强度 | ✔ 6 位数字 + `SecureRandom` | `generateCode` |
| 邮箱验证码频率 | ✔ 同邮箱同用途 60 秒间隔、同 IP 60 秒间隔、IP 每小时 10 封 | `send` 第 51–58 行 |
| 邮箱验证码错误上限 | ✔ 超过上限直接作废并提示重新获取 | `consume` 第 98–102 行 |
| 邮箱验证码一次性 | ✔ 校验通过立即删除 | `consume` 第 105 行 |
| 发信失败降级 | ✔ 发信失败时释放邮箱维度间隔，允许立刻重试 | `send` 第 67–71 行 |

**⚠ 发现 3（低危）：验证码在事务内部消费，事务回滚后码已失效**

`AuthService.createAccount` 第 187 行、`resetPassword` 第 239 行的 `emailCodeService.consume(...)` 都在 `transactionTemplate` 内部执行。若后续 DB 写入失败导致事务回滚，Redis 里的验证码已被删除，用户重试会收到「验证码错误或已过期」，只能重新获取 —— 不是安全问题，但会放大失败成本（叠加 60 秒重发间隔后体验较差）。

> 建议改法（任选其一）：① `consume` 移到事务提交之后（先校验不删、提交后删除）；② 保留失败时的幂等缓存，让同一 `Idempotency-Key` 重试直接返回上次结果。

**💡 发现 4（低危）：密码登录校验图形验证码，邮箱验证码登录不校验**

`loginByEmailCode` 没有 `captchaService.verify`。当前有「同邮箱 60 秒 + 同 IP 60 秒 + IP 每小时 10 封」兜底，爆破空间受限，可接受；但需在接口文档与前端提示里写明，避免评审时被判定为遗漏。

### 2.4 账号生命周期（PASS，含 1 个高危）

| 检查点 | 结论 | 证据 |
|---|---|---|
| 注册不得自动成管理员 | ✔ 一律绑定 `STUDENT`，未找到角色时只告警不降级放行 | `AuthService.createAccount` 第 203–211 行 |
| 注册并发去重 | ✔ 唯一键冲突转 10004 + `Idempotency-Key` 幂等 | `createAccount` 第 197–201 行、`register` 守卫 |
| 注册后立即可用 | ✔ 建号后 `StpUtil.login` 并返回 token；同时清空权限快照避免首鉴权拿空权限 | 同上 |
| 禁用账号无法登录 | ✔ 密码登录、邮箱登录两条路径都校验 `status == 0` → 403 + 10013 | `AuthService` 第 97、123 行 |
| 禁用后已登录会话失效 | ✔ 管理员禁用时 `kickout` + 清快照 | P2 `AdminService.updateStatus` |
| 密码强度策略 | ✔ `PasswordPolicy.validateMatch` 校验一致性与强度 | `AuthService` 第 159 行 |

**🔴 发现 5（高危）：禁用状态只在「登录入口」校验，鉴权链不查 `status`**

`isDisabled()` 仅被 `loginByPassword` / `loginByEmailCode` 调用；`currentUser()`、`StpInterfaceImpl.getPermissionList()`、Sa-Token 拦截器都**不检查 `sys_user.status`**。因此「禁用即失权」完全依赖 `kickout` 成功执行：

- 多节点部署时 `kickout` 只作用于当前节点可达的会话；
- `kickout` 抛异常时 `AdminService` 只记录日志（第 110–115 行主动容错）；
- 数据库手工改 `status`、或运维绕过后台改状态时，根本没有 `kickout` 动作。

上述任一情况下，被禁用用户持有的 Token 仍可继续访问所有已授权接口，属于「已禁用但还能用」的真实缺口。

> **修复分工（重要）**：这条主责在鉴权侧，即 **P2 自己**（不是 P1 的活）。建议在 `StpInterfaceImpl` 取快照前或 Sa-Token 拦截器里加一次轻量 `status` 校验（可复用 `RbacSnapshotCache` 的 TTL 缓存，另加 `status` 字段或单独缓存），命中 `status == 0` 直接按 401 + 10001（或 403 + 10013）拒绝。
> 本条已登记为 P2 待办，见《P2_接口权限改造清单.md》。

**🔴 发现 6（高危遗留）：`/api/kb/**` 被排除在登录校验之外**

`WebConfig.addInterceptors` 第 49–53 行：

```java
SaRouter.match("/api/**")
        .notMatch(PUBLIC_PATHS)
        .notMatch("/api/kb", "/api/kb/**")   // ← 遗留放行
        .check(r -> StpUtil.checkLogin())
```

P1 的交接说明里已把它列为待清理项，但目前**仍未删除**。虽然 `KnowledgeController` 的 9 个方法都带 `@SaCheckPermission`（注解内部会先校验登录），短期不至于裸奔，但这属于「白名单式永久放行」，一旦有新增接口忘记加注解，就是**匿名可访问**。必须在最终 PR 前删除这一行 `notMatch`。

---

## 三、问题清单汇总

| 编号 | 严重度 | 位置 | 问题 | 建议 | 主责 |
|---|---|---|---|---|---|
| 1 | 中 | `AuthService:92`、`LoginAttemptService.onFailure` | 账号不存在也累加账号维度失败次数，可定向锁定他人账号 | 拆两档计数，不存在账号只记 IP | P1 |
| 2 | 高 | `WebConfig:51` | `/api/kb/**` 排除登录校验（遗留放行） | 删除该 `notMatch` | P1（或 P2 代改，需确认文件归属） |
| 3 | 高 | `AuthService.isDisabled` / `StpInterfaceImpl` / 拦截器 | 禁用状态不进入鉴权链，仅靠 `kickout` | 鉴权侧增加 `status` 校验，fail-closed | **P2** |
| 4 | 低 | `AuthService:187,239` | 验证码在事务内消费，回滚后不可重试 | 提交后删除或幂等兜底 | P1 |
| 5 | 低 | `AuthService.loginByEmailCode` | 邮箱登录不校验图形验证码 | 文档写明，或有需要时补充 | P1 |
| 6 | 低 | `SessionService` vs `AdminService.updateStatus` | 吊销入口两套口径 | 统一到 `SessionService.logoutUser` | P1 + P2 |
| 7 | 低 | `AuthRateLimitService` | 固定窗口，切换瞬间可双倍突发 | 本期可接受，写进已知限制 | P1 |

> 严重度定义：高 = 可绕过鉴权或导致越权；中 = 可被用于定向攻击/拒绝服务；低 = 可用性或一致性。

---

## 四、复审条件

以下三项完成后，本 Review 结论可升级为 **Pass**：

1. 删除 `WebConfig` 中 `/api/kb/**` 的 `notMatch`（发现 2）
2. P1 修复「账号不存在计入账号锁定」（发现 1）
3. P2 在鉴权链路补 `status == 0` 兜底校验（发现 3）

复审方式：`mvn clean test` 全绿 + 上述三点的代码 diff 复核 + 补一条「禁用用户持旧 Token 访问受限接口」的集成用例。

---

## 五、审查时确认的正面项（可作为答辩素材）

1. **loginId = 用户主键**：`StpUtil.login(user.getId())`，与 P2 的 `StpInterfaceImpl` 解析口径一致，不存在「用 username 当 loginId 导致全站 403」的经典坑。
2. **登录响应直接复用 P2 权限快照**：`issueToken` / `currentUser` 都通过 `RbacSnapshotCache` 取 `roles` / `permissions`，保证「登录返回的角色权限」与「注解鉴权」同源，不会出现前端有按钮、后端却 403 的错位。
3. **错误码与 HTTP 状态双给**：认证异常走 `AuthExceptionHandler`（400/401/403/409/423/429 + 10000–10013），全局异常走 `GlobalExceptionHandler`，两者通过 `assignableTypes` 隔离，实测无 Bean 冲突。
4. **注册红线守住**：首个注册用户不会变成管理员，符合接口文档 3.2。
