package com.group4.kaoyan.modules.admin.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2 任务 6：唯一管理员保护与内置角色保护的单元测试。
 */
@DisplayName("P2-后台角色安全约束")
class AdminServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final RbacSnapshotCache cache = mock(RbacSnapshotCache.class);
    private final AccountStatusGuard accountStatusGuard = mock(AccountStatusGuard.class);
    private final AdminService service =
            new AdminService(userMapper, roleMapper, permissionMapper, cache, accountStatusGuard);

    private SysRole role(long id, String code) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleCode(code);
        r.setRoleName(code);
        r.setStatus(1);
        return r;
    }

    @Test
    @DisplayName("唯一管理员：解绑管理员角色被拒绝")
    void cannotUnbindLastAdmin() {
        SysRole admin = role(1L, RoleConstants.SYSTEM_ADMIN);
        when(roleMapper.selectById(1L)).thenReturn(admin);
        when(roleMapper.selectRoleCodesByUserId(9L)).thenReturn(List.of(RoleConstants.SYSTEM_ADMIN));
        when(roleMapper.countUsersWithRoleCode(RoleConstants.SYSTEM_ADMIN)).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.unbindRole(8L, 9L, 1L));
        assertTrue(e.getMessage().contains("唯一的系统管理员"));
    }

    @Test
    @DisplayName("非唯一管理员：解绑放行，并失效该用户快照")
    void canUnbindWhenMoreThanOneAdmin() {
        SysRole admin = role(1L, RoleConstants.SYSTEM_ADMIN);
        when(roleMapper.selectById(1L)).thenReturn(admin);
        when(roleMapper.selectRoleCodesByUserId(9L)).thenReturn(List.of(RoleConstants.SYSTEM_ADMIN));
        when(roleMapper.countUsersWithRoleCode(RoleConstants.SYSTEM_ADMIN)).thenReturn(2L);

        service.unbindRole(8L, 9L, 1L);
    }

    @Test
    @DisplayName("整体替换角色时不能把最后一个管理员降权")
    void cannotDowngradeLastAdmin() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("admin01");
        when(userMapper.selectById(9L)).thenReturn(user);
        when(roleMapper.selectRoleCodesByUserId(anyLong())).thenReturn(List.of(RoleConstants.SYSTEM_ADMIN));
        when(roleMapper.countUsersWithRoleCode(RoleConstants.SYSTEM_ADMIN)).thenReturn(1L);

        BizException e = assertThrows(BizException.class,
                () -> service.assignRoles(8L, 9L, List.of(2L)));
        assertTrue(e.getMessage().contains("唯一的系统管理员"));
    }

    @Test
    @DisplayName("当前操作者不能移除自己的系统管理员角色")
    void cannotRemoveOwnAdminRole() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("admin01");
        when(userMapper.selectById(9L)).thenReturn(user);
        when(roleMapper.selectRoleCodesByUserId(9L)).thenReturn(List.of(RoleConstants.SYSTEM_ADMIN));
        when(roleMapper.selectById(2L)).thenReturn(role(2L, RoleConstants.STUDENT));
        when(roleMapper.countUsersWithRoleCode(RoleConstants.SYSTEM_ADMIN)).thenReturn(2L);

        BizException e = assertThrows(BizException.class,
                () -> service.assignRoles(9L, 9L, List.of(2L)));
        assertTrue(e.getMessage().contains("不能移除自己的系统管理员角色"));
    }

    @Test
    @DisplayName("内置角色不允许删除")
    void cannotDeleteBuiltinRole() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, RoleConstants.STUDENT));

        BizException e = assertThrows(BizException.class, () -> service.deleteRole(3L));
        assertTrue(e.getMessage().contains("内置角色"));
    }

    @Test
    @DisplayName("仍有用户绑定的角色不允许删除")
    void cannotDeleteBoundRole() {
        when(roleMapper.selectById(4L)).thenReturn(role(4L, "CUSTOM_ROLE"));
        when(roleMapper.countUsersWithRoleCode("CUSTOM_ROLE")).thenReturn(3L);

        BizException e = assertThrows(BizException.class, () -> service.deleteRole(4L));
        assertTrue(e.getMessage().contains("仍有用户绑定"));
    }
}
