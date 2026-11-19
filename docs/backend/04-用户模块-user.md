# 04 用户模块 user

路径：`backend/src/main/java/com/group4/kaoyan/modules/user/`

```
modules/user/
├── entity/SysUser.java         对应 sys_user 表
├── mapper/UserMapper.java      BaseMapper，无自定义 SQL
├── service/UserService.java    本人资料 / 改密
└── controller/UserController.java  /api/user/*
```

这个模块只管**“本人资料”**：昵称、邮箱、头像、密码。
账号的登录名、角色、启停状态属于 `auth` / `admin` 模块，别混进来。

## SysUser

路径：`backend/src/main/java/com/group4/kaoyan/modules/user/entity/SysUser.java`

```java
@TableName("sys_user")
public class SysUser {
    private Long id;
    private String username;        // 登录名，唯一
    private String passwordHash;    // BCrypt，响应里要注意别外泄
    private String nickname;
    private String email;
    private String avatar;
    private Integer status;         // 1 正常 0 禁用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

> 注意：当前接口直接返回 `SysUser` 实体，`passwordHash` 会一并出现在 JSON 里。
> 前端不用它，但如果这套要对外，建议加 `@JsonIgnore` 或改成 DTO 返回。

## 接口

路径：`backend/src/main/java/com/group4/kaoyan/modules/user/controller/UserController.java`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/user/profile` | 登录即可 | 查本人资料 |
| PUT | `/api/user/profile` | 登录即可 | 改昵称/邮箱/头像 |
| PUT | `/api/user/password` | 登录即可 | 改密码（校验原密码） |

这三个接口**不需要额外权限码**，因为“改自己的资料”是登录用户的基本权利；
但要受 `WebConfig` 的登录拦截约束（`/api/**` 除登录注册外都要登录）。

## 改密逻辑

路径：`backend/src/main/java/com/group4/kaoyan/modules/user/service/UserService.java`

```java
public void changePassword(String oldPassword, String newPassword) {
    if (newPassword == null || newPassword.length() < 6) {
        throw new BizException("新密码至少 6 位");
    }
    SysUser user = profile();
    if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
        throw new BizException("原密码不正确");
    }
    SysUser update = new SysUser();
    update.setId(user.getId());
    update.setPasswordHash(BCrypt.hashpw(newPassword));
    update.setUpdateTime(LocalDateTime.now());
    userMapper.updateById(update);
}
```

- 必须带原密码，防止 token 被盗后直接改密；
- 新密码只校验长度 ≥ 6，没有复杂度规则，要加的话就在这里加；
- **改完密码不会让旧 token 失效**，因为 token 存在 Sa-Token 会话里与密码无关。要“改密即踢下线”，
  得在改完后 `StpUtil.logout(user.getId())` 或按设备清会话。

## 查询辅助

```java
/** 供其他模块按登录名查用户，避免各处重复拼条件 */
public SysUser findByUsername(String username) {
    return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
}
```

目前 `findByUsername` 还没有调用方（注册/登录各自用的条件查询），保留是为了后续做“按用户名找用户并提权”。

## 改这里会牵连什么

| 改动 | 影响 |
| --- | --- |
| 实体加字段 | `sys_user` 表要同步加列（`schema.sql` 里是 `CREATE TABLE IF NOT EXISTS`，已有库不会自动 ALTER） |
| 想隐藏 `passwordHash` | 会在 `/api/user/profile`、`/api/auth/me`、`/api/admin/users` 三个返回里同时生效 |
| 加密码复杂度 | 只影响本模块；`AuthService.register` 也做了一次“非空”校验，两处要一起改 |
