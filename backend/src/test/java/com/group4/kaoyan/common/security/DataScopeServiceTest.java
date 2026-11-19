package com.group4.kaoyan.common.security;

import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2 任务 3：数据范围服务测试。
 *
 * <p>要证明三件事：单角色范围正确、多角色取最宽、含未知角色时不放大权限。</p>
 */
@DisplayName("P2-数据范围服务")
class DataScopeServiceTest {

    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final RbacSnapshotCache cache = new RbacSnapshotCache(roleMapper, permissionMapper);
    private final DataScopeService service = new DataScopeService(cache);

    private void givenRoles(long userId, List<String> roles) {
        when(roleMapper.selectRoleCodesByUserId(userId)).thenReturn(roles);
        when(permissionMapper.selectPermCodesByUserId(userId)).thenReturn(List.of());
    }

    @Test
    @DisplayName("学生：SELF")
    void studentIsSelf() {
        givenRoles(1L, List.of(RoleConstants.STUDENT));
        assertEquals(DataScope.SELF, service.scopeOf(1L));
        assertFalse(service.hasDataScopeOf(1L, DataScope.ALL));
        assertFalse(service.hasDataScopeOf(1L, DataScope.MASKED));
        assertTrue(service.hasDataScopeOf(1L, DataScope.SELF));
    }

    @Test
    @DisplayName("系统管理员：ALL，且同时满足 MASKED")
    void adminIsAll() {
        givenRoles(2L, List.of(RoleConstants.SYSTEM_ADMIN));
        assertEquals(DataScope.ALL, service.scopeOf(2L));
        assertTrue(service.hasDataScopeOf(2L, DataScope.ALL));
        assertTrue(service.hasDataScopeOf(2L, DataScope.MASKED));
    }

    @Test
    @DisplayName("数据审计员：MASKED，但不能读全部")
    void auditorIsMasked() {
        givenRoles(3L, List.of(RoleConstants.DATA_AUDITOR));
        assertEquals(DataScope.MASKED, service.scopeOf(3L));
        assertTrue(service.hasDataScopeOf(3L, DataScope.MASKED));
        assertFalse(service.hasDataScopeOf(3L, DataScope.ALL));
    }

    @Test
    @DisplayName("多角色取最宽：STUDENT + DATA_AUDITOR = MASKED")
    void widestScopeWins() {
        givenRoles(4L, List.of(RoleConstants.STUDENT, RoleConstants.DATA_AUDITOR));
        assertEquals(DataScope.MASKED, service.scopeOf(4L));
    }

    @Test
    @DisplayName("空角色或未知角色码：降级 SELF，绝不放大权限")
    void unknownRoleDegradesToSelf() {
        givenRoles(5L, List.of());
        assertEquals(DataScope.SELF, service.scopeOf(5L));

        givenRoles(6L, List.of("WHATEVER_ROLE"));
        assertEquals(DataScope.SELF, service.scopeOf(6L));
    }

    @Test
    @DisplayName("userId 为空：SELF")
    void nullUserIsSelf() {
        assertEquals(DataScope.SELF, service.scopeOf(null));
    }
}
