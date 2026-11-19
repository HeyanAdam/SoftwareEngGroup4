# P1 Review 记录：对 P2 的 RBAC、数据范围与接口安全代码

> Review 人：P1（认证后端）　被 Review：P2（RBAC / 接口安全 / 集成）
> 依据：P1 分工文档第 7 节「P1 Review P2 的 RBAC 查询、数据范围、401/403 和管理员安全代码」
> 版本：V1（2026-11-19）　Review 对象：本分支已并入的 P2 代码 + 远端 `feat/auth-rbac`（`929ca1c`）

---

## 1. 结论

| 等级 | 数量 | 说明 |
|---|---|---|
| 阻断（必须改，否则不能合） | 2 | 分支基线错误带回归；数据范围服务未实现 |
| 应修（功能/要求缺项） | 4 | 禁用不踢下线、审计未落库、管理员初始化缺失、接口路径与匿名口未收口 |
| 建议 | 1 | 状态变更后快照未失效 |
| 通过 | 4 | 鉴权数据源、缓存失效策略、管理接口权限注解、错误码常量 |

总体判断：**RBAC 的"字典 + 矩阵 + 鉴权数据源 + 缓存失效"这部分是合格的，可以直接用**；
但 P2 文档里的任务 3（数据范围）、任务 5（管理员初始化）、任务 6 中的禁用联动、以及任务 9 的分支准备工作，**都还没有落到代码里**。

---

## 2. 阻断项

### 阻断 1：远端 `feat/auth-rbac` 的基线是旧 main，并且带了三处回退

远端 `929ca1c` 的 merge-base 是 `532ead1`，不是当前 `main`（`52ddb2c`）。相对当前 main 的差异里有：

| 回退内容 | 证据 | 影响 |
|---|---|---|
| 删除 `V2–V6` 迁移，把 `V1` 改名成 `db/schema.sql` | `git diff --name-status origin/main..origin/feat/auth-rbac` 显示 `D V2…V6`、`R100 V1 → db/schema.sql` | Flyway 失去迁移链，V6 的权限种子与四角色数据不会写入，`RbacInitializer` 补齐时前提就错了 |
| `pom.xml` 去掉 `flyway-core`、`flyway-mysql` 与 lombok 1.18.38 覆盖 | 同上，`M backend/pom.xml` | 建库方式与 main 冲突，编译注解处理器行为不一致 |
| `KnowledgeService` / `RagChatService` / `RetrievalService` 角色校验写成 `"ADMIN"` | 三个文件均 `M` | `ADMIN` 已被 V6 迁移为 `SYSTEM_ADMIN` 并删除，写死该码等于知识库接口对内容运营完全放不开；同时 `RoleConstants.isBuiltin("ADMIN")` 明确为 false，两处自相矛盾 |

**处理**：这条分支不能直接 PR。请从 `feat/auth-foundation` 重新切 `feat/auth-backend-rbac`，把 RBAC 相关文件搬过去，**不要搬运**上述三类回退文件。

### 阻断 2：任务 3「数据范围服务」没有实现

`common/security/DataScope.java` 目前只有：

```java
public enum DataScope {                 // ALL / SELF / MASKED
    public boolean canReadAll() { ... }
    public static DataScope of(String value) { ... }
}
```

没有"给定当前用户 + 目标资源，判断是否越权"的服务，也没有任何查询带数据范围过滤。
后果：分工文档要求的「数据归属失败 → HTTP 403 + 10009」没有落地路径，P3 的 403 页面只能验证"接口权限"，验证不了"越权访问"。

**需要补的最小形态**：

```java
// 建议放在 common/security/DataScopeService
public void checkReadable(Long currentUserId, Long ownerUserId);
// SELF 且 owner != current -> AuthException(10009, 403, "无权访问该数据")
// MASKED 且访问明细 -> 同样 10009
```

---

## 3. 应修项

### 应修 3：管理员禁用用户没有吊销会话

`modules/admin/service/AdminService.updateStatus(Long userId, Integer status)` 只做了 `userMapper.updateById`，**没有调用 `SessionService.logoutUser(userId)`**。

后果：被禁用用户的旧 Token 在 30 天内继续有效，`StpUtil.checkLogin()` 依然通过——禁用等于没禁。
这是我交接文档《P1交付给P2_认证与鉴权契约》第 4 节明确要求的调用点，也符合 P2 文档任务 3「用户禁用后清理缓存」。

### 应修 4：审计日志没有落库（任务 5 / 6 的「写入审计日志」）

- 表是**存在**的：`V3__idempotency_and_audit.sql` 里 `CREATE TABLE IF NOT EXISTS sys_audit_log`，不是"待数据库成员建表"。
- 但 `AdminService` 里没有任何向 `sys_audit_log` 写入的代码，角色调整、状态变更都没有留痕。

**注意**：这条是 P2 自己的任务（5/6），不属于 P1；提出来的目的是纠正"表不存在"这个前提——表已经有了，直接写就行。

### 应修 5：任务 5「管理员受控初始化」没有实现

没有"检查系统管理员 → 创建管理员 → BCrypt → 绑定 SYSTEM_ADMIN → 审计 → 提示改密"的任何代码。当前新用户注册一律 `STUDENT`（符合接口文档 3.2），所以**系统里现在没有任何管理员**，后台接口一个都打不开。
需要按 P2 文档任务 5 做受控初始化（环境变量或初始化命令），不要退回到"首个注册用户当管理员"——接口文档 3.2 明确禁止。

### 应修 6：管理接口路径与权限码清单不一致，`/api/kb/**` 仍匿名

- `AdminController` 的 `@RequestMapping("/api/admin")`，而《权限码清单》登记的是 `GET /api/v1/admin/users` 等。路径缺 `/v1` 前缀，P3 菜单和 P2 自己的接口权限表都会对不上。
- `WebConfig` 里还留着 `.notMatch("/api/kb", "/api/kb/**")`，知识库接口仍匿名（P2 文档任务 7 未收口）。
- `AdminController` 八个端点都加了 `@SaCheckPermission`，这点是**通过**的；缺的是知识库、知识文档两组接口的权限声明。

---

## 4. 建议项

### 建议 7：状态变更后快照未失效

`assignRoles` / `assignPermissions` 都有 `evict` / `evictByRoleId`（做得对）。但 `updateStatus` 改状态后没有 `snapshotCache.evict(userId)`。
建议与应修 3 一起处理：禁用时先 `logoutUser`，再 `evict`。

---

## 5. 通过项（不用改）

| 项 | 结论 |
|---|---|
| 鉴权数据源 `StpInterfaceImpl` | 走 `RbacSnapshotCache`，fail-closed，loginId 非数字返回空权限——正确，P1 的登录/`me` 已与之同源 |
| 缓存策略 `RbacSnapshotCache` | TTL 5 分钟 + 主动失效，`evict` / `evictByRoleId` / `evictAll` 齐备 |
| 权限字典 `PermissionConstants` | 20 条与《权限表》完全一致，`PermissionCatalogTest` 10 项守卫到位 |
| 角色常量 `RoleConstants` | 4 个官方角色码、默认授权数 20/1/6/3、数据范围 ALL/SELF/ALL/MASKED，与文档一致；`ADMIN`/`KB_ADMIN`/`DATA_VIEWER` 已判为非内置 |

---

## 6. 复核（2026-11-19，对象：`feat/auth-foundation` @ `9c8135b`）

P2 已创建 `feat/auth-foundation`，并把 P1 分支（`f66f634`…`fbebb19`）与 RBAC 分支合并进来（合并提交 `6042aa0`）。在该分支上 `mvn test` 实测：**82 项，失败 0，跳过 2**。逐条复核上文的结论：

| 编号 | 原结论 | 复核结果 |
|---|---|---|
| 阻断 1 | `feat/auth-rbac` 带回归 | **已解决**。foundation 保留 `V1–V6` 全部迁移；`pom.xml` 有 `flyway-core`、`flyway-mysql`、`spring-boot-starter-mail`、lombok 1.18.38；知识库/对话改用 `RoleConstants`，不再写死 `"ADMIN"` |
| 阻断 2 | 数据范围服务未实现 | **已解决**。新增 `common/security/DataScopeService` + `DataScopeServiceTest`，越权可抛 10009 |
| 应修 3 | 禁用不踢下线 | **部分解决**。`AdminService.updateStatus` 已踢下线，但用的是 `StpUtil.logout(userId)`，不是 `SessionService.logoutUser(userId)`。Sa-Token 配置为 `is-share: false`，一个账号可能有多 Token，`logout(loginId)` 只覆盖活跃那一个，**建议改调 `SessionService.logoutUser` 一次性吊销全部 Token** |
| 应修 4 | 审计未落库 | **未解决**。`sys_audit_log` 表在 V3 已建，但代码里仍只有 `log.info`，没有 `AuditLog` 实体与 Mapper |
| 应修 5 | 管理员初始化缺失 | **已解决**。新增 `modules/auth/init/AdminBootstrapInitializer`（受控建管理员，未回退到"首用户当管理员"） |
| 应修 6 | 管理接口路径、`/api/kb` 匿名 | **未解决**。`AdminController` 仍是 `@RequestMapping("/api/admin")`，与《权限码清单》登记的 `/api/v1/admin/**` 不一致；`WebConfig` 的 `/api/kb` 白名单仍在 |
| 建议 7 | 状态变更后未清快照 | **未解决**。`updateStatus` 仍未 `snapshotCache.evict(userId)` |

**P1 侧确认**：foundation 合并过程中**没有改动 P1 的认证代码**（`AuthService`、`AuthController`、DTO/VO、各 Service、`AuthExceptionHandler` 全部与我分支一致），仅 `SysRole` 注释措辞、`RbacInitializer` 加 `@Order(1)` 这类无害调整。

### 新发现（阻断级，需 P2 处理）

**foundation 根目录混进了 120 个文件的嵌套副本 `SoftwareEngGroup4-main/`**（来自 P2 本地 zip 的旧 main 拷贝），与真正的 `backend/`、`deploy/`、`frontend/` 并列存在。后果：仓库里出现两份工程，评审会认定为误提交，且后续 `git grep`、CI 路径匹配都可能被误导。

处理：`git rm -r --cached SoftwareEngGroup4-main && git commit`，一次清理干净，不影响 `backend/` 等真实目录。

---

## 7. 请 P2 回的四个问题

1. `feat/auth-rbac` 是否直接从 `feat/auth-foundation` 重切？现在这版带着删迁移的回退，不能合。
2. `DataScopeService`（任务 3）本轮做不做？不做的话，"越权 → 10009"这条在验收里就是空的。
3. 管理员受控初始化（任务 5）由谁做、什么时候做？没有它后台全 403，P3 的权限菜单也没法验证。
4. 管理接口路径统一成 `/api/v1/admin/**` 还是保持 `/api/admin/**`？改哪个都行，但要和《权限码清单》对齐。
