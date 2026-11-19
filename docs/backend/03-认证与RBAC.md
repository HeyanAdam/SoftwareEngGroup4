# 03 认证与 RBAC（授权 + 鉴权）

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/`

```
modules/auth/
├── controller/AuthController.java     注册 / 登录 / me / 登出
├── service/AuthService.java           注册（首个用户=管理员）、登录、当前用户
├── service/StpInterfaceImpl.java      Sa-Token 的权限数据源（从库读）
├── init/RbacInitializer.java          启动时补角色与权限字典（不预置账号）
├── mapper/RoleMapper.java             sys_role + sys_user_role
├── mapper/PermissionMapper.java       sys_permission + sys_role_permission
└── entity/SysRole.java  SysPermission.java
```

配套的通用配置在：
`backend/src/main/java/com/group4/kaoyan/common/config/WebConfig.java`（登录拦截）
`backend/src/main/java/com/group4/kaoyan/common/GlobalExceptionHandler.java`（鉴权异常转 401/403）
`backend/src/main/resources/config/application.yml` 的 `sa-token` 段（token 规则）

---

## 一、概念先分清

| 概念 | 回答的问题 | 在本项目里落在哪 |
| --- | --- | --- |
| **认证** Authentication | 你是谁？登录了没有？ | `SaInterceptor` + `StpUtil.checkLogin()` |
| **授权** Authorization | 你能做什么？（给角色/权限赋值） | `RbacInitializer`、`AdminService.assignRoles/assignPermissions` |
| **鉴权** | 这次请求允许吗？（每次请求的判定） | `@SaCheckPermission` + 服务层 `requireAdmin()` |

授权是**写数据**（谁拥有什么权限），鉴权是**每次请求的判定**。两者共用同一批表，
所以后台改完权限，下一个请求立刻生效（因为每次请求都查库，见第四节）。

---

## 二、五张表（唯一事实来源）

路径：`backend/src/main/resources/db/schema.sql`

```sql
sys_user(id, username UNIQUE, password_hash, nickname, email, avatar, status, ...)
sys_role(id, role_code UNIQUE, role_name, description, status, ...)
sys_permission(id, perm_code UNIQUE, perm_name, perm_type, parent_id, path, sort)
sys_user_role(user_id, role_id)              UNIQUE KEY uk_user_role
sys_role_permission(role_id, permission_id)  UNIQUE KEY uk_role_permission
```

关系就是一条链：

```
用户 ──(sys_user_role)── 角色 ──(sys_role_permission)── 权限码
```

- 没有“用户直接挂权限”的表，权限只能从角色继承，避免两套来源打架；
- 两个关联表都有唯一键，`bindUserRole` / `bindRolePermission` 用的是 `INSERT IGNORE`，
  重复绑定不会产生脏数据；
- 密码字段 `password_hash` 用 BCrypt（`cn.hutool.crypto.digest.BCrypt`），明文不落库。

---

## 三、认证链路：从请求到“你是谁”

### 1) token 规则

路径：`backend/src/main/resources/config/application.yml`

```yaml
sa-token:
  token-name: Authorization        # 请求头名字
  token-prefix: Bearer             # 值形如：Bearer xxxx
  timeout: 2592000                 # 30 天
  active-timeout: -1               # 不做活跃超时
  is-concurrent: true              # 允许同一账号多处登录
  is-share: false                  # 每次登录发新 token
  token-style: uuid
```

前端要带：`Authorization: Bearer <token>`。

### 2) 登录拦截

路径：`backend/src/main/java/com/group4/kaoyan/common/config/WebConfig.java`

```java
registry.addInterceptor(new SaInterceptor(handler -> SaRouter.match("/api/**")
                .notMatch("/api/auth/login", "/api/auth/register")
                .check(r -> StpUtil.checkLogin())))
        .addPathPatterns("/**");
```

- `/api/**` 一律要求登录，只有登录/注册放行；
- 未登录 → 抛 `NotLoginException` → `GlobalExceptionHandler` 转成 `{"code":401,"message":"未登录或登录已失效"}`；
- **这一步只判登录，不判权限**；漏配权限注解的接口，任何登录用户都能调，所以新增接口必须写注解。

### 3) 登录接口做什么

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/service/AuthService.java`

```java
public LoginResult login(String username, String rawPassword) {
    SysUser user = userMapper.selectOne(... eq username ...);
    if (user == null || !BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
        throw new BizException(401, "用户名或密码错误");
    }
    if (user.getStatus() != null && user.getStatus() == 0) {
        throw new BizException(403, "账号已被禁用");
    }
    StpUtil.login(user.getId());                       // ① 建立会话，签发 token
    String token = StpUtil.getTokenValue();
    List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
    List<String> perms = permissionMapper.selectPermCodesByUserId(user.getId());
    return new LoginResult(token, user.getId(), user.getUsername(), user.getNickname(), roles, perms);
}
```

要点：

- `StpUtil.login(user.getId())` 传的是 **userId**，所以后续 `StpUtil.getLoginIdAsLong()` 拿到的就是用户 id；
- 返回体里带 `roles` / `permissions`，前端可以据此决定菜单显不显示——**但前端隐藏只是体验，
  后端注解才是真正的门**；
- `status=0`（禁用）在登录时就拦掉，已登录用户的禁用靠后台改状态 + 下次登录生效（token 不会立刻失效）。

---

## 四、鉴权链路：每次请求怎么判定

### 1) 权限数据源：StpInterfaceImpl

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/service/StpInterfaceImpl.java`

```java
@Component
public class StpInterfaceImpl implements StpInterface {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionMapper.selectPermCodesByUserId(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleMapper.selectRoleCodesByUserId(Long.valueOf(loginId.toString()));
    }
}
```

Sa-Token 的 `@SaCheckPermission` / `@SaCheckRole` / `StpUtil.hasRole` 全部走这两个方法。
**没有加缓存**，所以每次判定都是两条 JOIN 查询——好处是后台改权限立刻生效，坏处是 QPS 高时要加缓存。

对应 SQL（`PermissionMapper`）：

```sql
SELECT DISTINCT p.perm_code
FROM sys_permission p
JOIN sys_role_permission rp ON p.id = rp.permission_id
JOIN sys_user_role ur ON rp.role_id = ur.role_id
WHERE ur.user_id = #{userId}
```

### 2) 接口层：注解

路径：`backend/src/main/java/com/group4/kaoyan/modules/knowledge/controller/KnowledgeController.java`

```java
@SaCheckPermission("kb:create")
@PostMapping("/kb")
public R<KbKnowledgeBase> create(@RequestBody KbCreateReq req) { ... }

@SaCheckPermission("kb:view")
@GetMapping("/kb")
public List<KbKnowledgeBase> list() { ... }
```

失败抛 `NotPermissionException`，全局异常处理转：

```json
{"code":403,"message":"缺少权限：kb:create"}
```

### 3) 服务层：二次校验（防漏网）

路径：`backend/src/main/java/com/group4/kaoyan/modules/knowledge/service/KnowledgeService.java`

```java
private void requireAdmin(String message) {
    if (!isAdmin()) {                       // StpUtil.hasRole("ADMIN")
        throw new BizException(403, message);
    }
}

/** 知识库读取：仅管理员；普通用户没有任何浏览入口 */
private KbKnowledgeBase requireKb(Long kbId) {
    requireAdmin("普通用户不可访问知识库");
    KbKnowledgeBase kb = kbMapper.selectById(kbId);
    if (kb == null) throw new BizException(404, "知识库不存在");
    return kb;
}
```

为什么注解之外还要查一遍：权限码是可以被管理员在控制台改的（比如误把 `kb:create` 配给 STUDENT），
服务层这一层保证“即使配错，普通用户也进不来”。

### 4) 数据层：检索过滤

路径：`backend/src/main/java/com/group4/kaoyan/modules/knowledge/service/RetrievalService.java`

普通用户检索时**不按 user_id 过滤**，而是按“公开知识库的 kb_id 集合”过滤：

```java
if (StpUtil.hasRole("ADMIN")) {
    if (kbId == null) return "id >= 0";                 // 管理员：全量
    if (kbMapper.selectById(kbId) == null) throw new BizException(404, "知识库不存在");
    return "kb_id == " + kbId;                          // 管理员：指定库
}
// 普通用户：忽略传参 kbId，只检索 visibility=1 的公开库
return "kb_id in [" + publicKbIds + "]";
```

用 `user_id` 过滤会造成“管理员建的库、普通用户检索不到”，所以必须按 `kb_id` 集合。

### 5) 输出层：引用分级

路径：`backend/src/main/java/com/group4/kaoyan/modules/chat/service/RagChatService.java`

```java
private List<Map<String, Object>> toRefs(List<RetrievalService.Retrieved> hits) {
    if (!StpUtil.hasRole("ADMIN")) {
        return List.of();      // 普通用户：连文档名都不给
    }
    // 管理员：index / docName / docId / chunkId / score / snippet
}
```

---

## 五、权限码全集与角色默认值

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/init/RbacInitializer.java`

```java
private static final String[][] PERMISSIONS = {
        {"kb:view", "查看知识库"}, {"kb:create", "创建知识库"}, {"kb:delete", "删除知识库"},
        {"doc:upload", "上传文档"}, {"doc:delete", "删除文档"}, {"chat:send", "智能问答"},
        {"admin:user:list", "用户列表"}, {"admin:user:update", "用户管理"},
        {"admin:role:list", "角色列表"}, {"admin:role:update", "角色权限管理"},
        {"admin:vector:manage", "向量库管理"}
};

/** STUDENT 首次创建时写入的默认权限 */
private static final List<String> STUDENT_DEFAULT_PERMS = Arrays.asList("chat:send");
```

| 权限码 | 控制的接口 |
| --- | --- |
| `kb:view` | `GET /api/kb`、`GET /api/kb/{id}`、`GET /api/kb/{id}/documents`、`GET /api/doc/{id}` |
| `kb:create` | `POST /api/kb` |
| `kb:delete` | `DELETE /api/kb/{id}` |
| `doc:upload` | `POST /api/kb/{id}/documents`、`POST /api/doc/{id}/reparse` |
| `doc:delete` | `DELETE /api/doc/{id}` |
| `chat:send` | `POST /api/chat/stream` |
| `admin:user:list` | `GET /api/admin/users`、`GET /api/admin/users/{id}/roles` |
| `admin:user:update` | `PUT /api/admin/users/{id}/status`、`PUT /api/admin/users/{id}/roles` |
| `admin:role:list` | `GET /api/admin/roles`、`GET /api/admin/permissions` |
| `admin:role:update` | `POST /api/admin/roles`、`PUT /api/admin/roles/{id}/permissions` |
| `admin:vector:manage` | `POST /api/admin/vector/reindex/{kbId}` |

角色：

| 角色 | 默认权限 | 说明 |
| --- | --- | --- |
| `ADMIN` | 全部 11 条 | 只有 ADMIN 能碰知识库与后台 |
| `STUDENT` | 只有 `chat:send` | 默认注册角色 |

---

## 六、授权：管理员账号怎么产生、权限怎么改

### 1) 不预置账号，第一个注册的人成为管理员

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/service/AuthService.java`

```java
@Transactional
public synchronized void register(String username, String rawPassword, String nickname) {
    ... 校验重名、插入 sys_user ...

    SysRole role = resolveRegisterRole();
    roleMapper.bindUserRole(user.getId(), role.getId());
    if (ROLE_ADMIN.equals(role.getRoleCode())) {
        log.info("系统内暂无管理员，首个注册用户 {} 已自动成为管理员", user.getUsername());
    }
}

/** 库里一个管理员都没有时，这个用户就是管理员（只发生一次） */
private SysRole resolveRegisterRole() {
    if (roleMapper.countUsersWithRoleCode(ROLE_ADMIN) == 0) {
        SysRole admin = findRole(ROLE_ADMIN);
        if (admin != null) return admin;
    }
    return findRole(ROLE_STUDENT);
}
```

判断“有没有管理员”用的是 `RoleMapper.countUsersWithRoleCode`：

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/mapper/RoleMapper.java`

```java
@Select("SELECT COUNT(DISTINCT ur.user_id) FROM sys_user_role ur "
        + "JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = #{roleCode}")
long countUsersWithRoleCode(@Param("roleCode") String roleCode);
```

`register` 加了 `synchronized`：否则两个并发注册可能同时读到“管理员数为 0”，双双变成管理员。

### 2) 启动初始化：只补缺，不覆盖

路径：`backend/src/main/java/com/group4/kaoyan/modules/auth/init/RbacInitializer.java`

```java
@Override
@Transactional
public void run(ApplicationArguments args) {
    Map<String, Long> permIds = new LinkedHashMap<>();
    for (String[] p : PERMISSIONS) {
        permIds.put(p[0], ensurePermission(p[0], p[1]));     // 缺的权限码补进字典
    }
    ensureRole(ROLE_ADMIN, "管理员", "拥有全部权限", permIds.keySet(), permIds);
    ensureRole(ROLE_STUDENT, "学生", "默认注册角色，只能问答", STUDENT_DEFAULT_PERMS, permIds);
}

private boolean ensureRole(...) {
    SysRole exist = roleMapper.selectOne(... eq roleCode ...);
    if (exist != null) {
        log.info("角色 {} 已存在，沿用数据库里的权限配置（可在后台修改）", code);
        return false;                                        // 已存在就完全不动
    }
    ... 新建角色并写入默认权限 ...
}
```

这解决了一个很实际的问题：如果每次启动都“重置 STUDENT 权限”，管理员在后台调过的配置重启就白调了。
现在的规则是**角色只在第一次创建时写默认权限，之后以数据库为准**。

### 3) 后台改权限（授权的写入口）

路径：`backend/src/main/java/com/group4/kaoyan/modules/admin/service/AdminService.java`

```java
/** 给用户分配角色（提权 / 降权） */
@Transactional
public void assignRoles(Long userId, List<Long> roleIds) {
    ...
    boolean targetIsAdmin = roleMapper.selectRoleCodesByUserId(userId).contains(ROLE_ADMIN);
    boolean keepAdmin = roleIds != null && roleIds.stream()
            .map(roleMapper::selectById)
            .anyMatch(r -> r != null && ROLE_ADMIN.equals(r.getRoleCode()));
    if (targetIsAdmin && !keepAdmin && roleMapper.countUsersWithRoleCode(ROLE_ADMIN) <= 1) {
        throw new BizException("这是当前唯一的系统管理员，不能降权；请先把其他用户提权");
    }
    roleMapper.unbindUserRoles(userId);                     // 先清空
    roleIds.forEach(rid -> roleMapper.bindUserRole(userId, rid));
}

/** 给角色分配权限 */
@Transactional
public void assignPermissions(Long roleId, List<Long> permissionIds) {
    permissionMapper.unbindRolePermissions(roleId);
    permissionIds.forEach(pid -> permissionMapper.bindRolePermission(roleId, pid));
}
```

对应接口（`AdminController`）：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| PUT | `/api/admin/users/{id}/roles` | 改用户角色（提权/降权），body `{"ids":[2,3]}` |
| PUT | `/api/admin/roles/{id}/permissions` | 改角色权限，body `{"ids":[1,6]}` |
| POST | `/api/admin/roles` | 新建角色（可为不同人群做不同权限集） |

### 4) 初始化与持久化对照

| 动作 | 写哪张表 | 重启后 |
| --- | --- | --- |
| 首次启动 | 建 `ADMIN`/`STUDENT`、11 条权限码，并给角色写默认权限 | 保留；再次启动只补缺 |
| 第一次注册 | 该用户绑 `ADMIN` | 保留（`sys_user_role`） |
| 后续注册 | 绑 `STUDENT` | 保留 |
| 后台提权 | 改 `sys_user_role` | 保留 |
| 后台调权限 | 改 `sys_role_permission` | 保留 |
| 账号密码 | `sys_user` | 保留（存在 MySQL 卷里） |

---

## 七、鉴权失败长什么样

| 场景 | 返回 |
| --- | --- |
| 没带 token / token 失效 | `{"code":401,"message":"未登录或登录已失效"}` |
| 有 token 但缺权限码 | `{"code":403,"message":"缺少权限：doc:upload"}` |
| 有 token 但缺角色（`@SaCheckRole`） | `{"code":403,"message":"缺少角色：ADMIN"}` |
| 服务层兜底拦截 | `{"code":403,"message":"普通用户不可浏览知识库"}`（业务自定义文案） |

---

## 八、新增一个权限码 / 新增一个管理接口，要动哪些文件

1. `backend/src/main/java/com/group4/kaoyan/modules/auth/init/RbacInitializer.java`
   —— 往 `PERMISSIONS` 数组加一行 `{"xxx:yyy", "说明"}`。**注意：只有角色首次创建时才会绑定默认权限，
   老库里新加的权限码不会自动授予任何角色**，需要到后台给 ADMIN 勾上，或手写 SQL 绑 `sys_role_permission`。
2. 目标 controller 的方法上加 `@SaCheckPermission("xxx:yyy")`。
3. 如果这个接口会碰 Milvus / MinIO，再在 service 里加一层 `requireAdmin(...)`。
4. 前端只在“按钮显示”层面用权限码，别在页面上把它当唯一防线。

## 九、已知限制（写代码时注意）

| 限制 | 现况 | 影响 |
| --- | --- | --- |
| 权限无缓存 | 每次 `@SaCheckPermission` 都查两次 JOIN | 高频接口有额外 DB 压力 |
| 注册接口匿名开放 | 谁先注册谁是管理员 | 部署到公网要先拿管理员再关接口或加邀请码 |
| token 无刷新 | 30 天到期后需重新登录 | 前端要处理 401 |
| 禁用用户不会立刻踢下线 | 仅登录时校验 `status` | 需要“立即封禁”得清 Sa-Token 会话 |
| STUDENT 权限可被改 | 后台可给它加权限码 | 服务层 `requireAdmin` 是最后防线 |
