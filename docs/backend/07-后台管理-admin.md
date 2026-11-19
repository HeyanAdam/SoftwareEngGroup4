# 07 后台管理 admin

路径：`backend/src/main/java/com/group4/kaoyan/modules/admin/`

```
modules/admin/
├── controller/AdminController.java    /api/admin/*
└── service/AdminService.java          用户/角色/权限维护 + 唯一管理员保护
```

这个模块是 **RBAC 的维护入口**：管理员在这里改“谁能做什么”。鉴权判定本身不在这里
（在 `auth` 模块的 `StpInterfaceImpl` 和接口注解），这里只**写数据**。

---

## 一、接口清单

路径：`backend/src/main/java/com/group4/kaoyan/modules/admin/controller/AdminController.java`

| 方法 | 路径 | 权限码 | 作用 |
| --- | --- | --- | --- |
| GET | `/api/admin/users` | `admin:user:list` | 分页查用户（`keyword/page/size`） |
| PUT | `/api/admin/users/{id}/status` | `admin:user:update` | 启用/禁用，body `{"status":1}` |
| PUT | `/api/admin/users/{id}/roles` | `admin:user:update` | 改角色（提权/降权），body `{"ids":[1,2]}` |
| GET | `/api/admin/users/{id}/roles` | `admin:user:list` | 查某人有哪些角色 |
| GET | `/api/admin/roles` | `admin:role:list` | 角色列表 |
| POST | `/api/admin/roles` | `admin:role:update` | 新建角色 |
| GET | `/api/admin/permissions` | `admin:role:list` | 权限码字典（前端勾选用） |
| PUT | `/api/admin/roles/{id}/permissions` | `admin:role:update` | 改角色权限，body `{"ids":[1,6]}` |
| POST | `/api/admin/vector/reindex/{kbId}` | `admin:vector:manage` | 重建整个知识库向量，返回文档数 |

权限码共 5 个管理类：`admin:user:list`、`admin:user:update`、`admin:role:list`、
`admin:role:update`、`admin:vector:manage`，默认只授予 `ADMIN`。

---

## 二、用户分页

路径：`backend/src/main/java/com/group4/kaoyan/modules/admin/service/AdminService.java`

```java
public PageData<SysUser> pageUsers(String keyword, long page, long size) {
    Page<SysUser> p = new Page<>(page, size);
    IPage<SysUser> result = userMapper.selectPage(p, Wrappers.<SysUser>lambdaQuery()
            .and(keyword != null && !keyword.isBlank(),
                    w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
            .orderByDesc(SysUser::getId));
    return PageData.of(result.getTotal(), result.getRecords());
}
```

分页能生效依赖 `WebConfig` 里注册的 `PaginationInnerInterceptor(DbType.MYSQL)`；
少了它 `total` 会是 0。

返回体：

```json
{"code":0,"data":{"total":42,"records":[{"id":1,"username":"xuxu",...}]}}
```

---

## 三、提权 / 降权

```java
@Transactional
public void assignRoles(Long userId, List<Long> roleIds) {
    if (userMapper.selectById(userId) == null) throw new BizException("用户不存在");

    // 兜底：不能把系统里最后一个管理员降权，否则谁都没法再进后台
    boolean targetIsAdmin = roleMapper.selectRoleCodesByUserId(userId).contains(ROLE_ADMIN);
    boolean keepAdmin = roleIds != null && roleIds.stream()
            .map(roleMapper::selectById)
            .anyMatch(r -> r != null && ROLE_ADMIN.equals(r.getRoleCode()));
    if (targetIsAdmin && !keepAdmin && roleMapper.countUsersWithRoleCode(ROLE_ADMIN) <= 1) {
        throw new BizException("这是当前唯一的系统管理员，不能降权；请先把其他用户提权");
    }

    roleMapper.unbindUserRoles(userId);
    roleIds.forEach(rid -> roleMapper.bindUserRole(userId, rid));
}
```

要点：

- 语义是**整体替换**，不是追加：传 `[2]` 就只剩 STUDENT，原来的 ADMIN 会被去掉；
- 传空数组 `[]` 会让用户没有任何角色（就没有任何权限码），等于“只能看自己的资料”；
- 唯一管理员保护只是防手滑，真要换管理员：先把新人提权，再降旧的；
- 角色 id 从 `GET /api/admin/roles` 拿，`ADMIN` 的 id 不保证是 1（取决于初始化顺序），让前端按 `roleCode` 认。

---

## 四、角色权限维护

```java
@Transactional
public void assignPermissions(Long roleId, List<Long> permissionIds) {
    if (roleMapper.selectById(roleId) == null) throw new BizException("角色不存在");
    permissionMapper.unbindRolePermissions(roleId);       // 整体替换
    permissionIds.forEach(pid -> permissionMapper.bindRolePermission(roleId, pid));
}

@Transactional
public SysRole createRole(String roleCode, String roleName, String description) {
    if (roleMapper.selectCount(... eq roleCode ...) > 0) throw new BizException("角色标识已存在");
    ...
}
```

可选的权限码从 `GET /api/admin/permissions` 拿（11 条，见 `03-认证与RBAC.md` 的对照表）。

### 改权限后立刻生效

因为 `StpInterfaceImpl` 每次都查库（无缓存），管理员保存后，目标用户**下一个请求**就按新权限判定，
不需要重新登录。反过来，如果要加缓存，就得处理“改完不清缓存”的一致性问题。

### 注意 STUDENT 的默认权限

`RbacInitializer` **只在角色首次创建时**写默认权限。所以：

- 老库里给 STUDENT 新加权限码 → 不会自动授予；
- 想让所有普通用户都多一个权限 → 在后台改 STUDENT 的权限集，或者给他们新建角色再提权；
- 重启不会把你改过的配置还原（这是有意为之，见 03 文档）。

---

## 五、向量库维护

```java
@SaCheckPermission("admin:vector:manage")
@PostMapping("/vector/reindex/{kbId}")
public R<Integer> reindex(@PathVariable Long kbId) {
    return R.ok(knowledgeService.reindexKnowledgeBase(kbId));
}
```

真正干活的是 `KnowledgeService.reindexKnowledgeBase`：逐文档清向量、清切片、状态回 `UPLOADED`、
重新入队，返回提交的文档数（**不是处理完成的文档数**，异步跑）。

适合的场景：换 embedding 模型后全量重算、Milvus 数据丢失后修复、切片策略调整后重建。

---

## 六、改这里会牵连什么

| 改动 | 影响 |
| --- | --- |
| 新增管理接口 | 必须加 `@SaCheckPermission`，否则登录用户都能调（`/api/**` 只拦登录） |
| 新增权限码 | 同时改 `RbacInitializer.PERMISSIONS`，否则后台勾选框里没有它 |
| 改 `assignRoles` 语义为“追加” | 前端“取消勾选”会不生效，慎改 |
| 去掉唯一管理员保护 | 管理员可能把自己锁在门外 |
| 分页大小不限制 | `size` 由前端传，建议加个上限（当前没加） |
