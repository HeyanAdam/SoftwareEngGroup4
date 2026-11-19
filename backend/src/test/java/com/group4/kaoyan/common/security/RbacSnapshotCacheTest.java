package com.group4.kaoyan.common.security;

import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P2 任务 2：权限快照缓存的命中、失效与 TTL 测试。
 *
 * <p>核心要证明的三点：<b>不重复查库</b>、<b>改授权后能主动失效</b>、<b>空 loginId 不查库</b>。</p>
 */
@DisplayName("P2-权限快照缓存")
class RbacSnapshotCacheTest {

    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);

    private void givenUser(int userId, List<String> roles, List<String> perms) {
        when(roleMapper.selectRoleCodesByUserId((long) userId)).thenReturn(roles);
        when(permissionMapper.selectPermCodesByUserId((long) userId)).thenReturn(perms);
    }

    @Test
    @DisplayName("同一用户在 TTL 内只查一次库")
    void cachesWithinTtl() {
        givenUser(1, List.of("STUDENT"), List.of("chat:send"));
        RbacSnapshotCache cache = new RbacSnapshotCache(roleMapper, permissionMapper);

        assertEquals(List.of("chat:send"), cache.get(1L).permissions());
        assertEquals(List.of("chat:send"), cache.get(1L).permissions());
        assertEquals(List.of("STUDENT"), cache.get(1L).roles());

        verify(roleMapper, times(1)).selectRoleCodesByUserId(1L);
        verify(permissionMapper, times(1)).selectPermCodesByUserId(1L);
    }

    @Test
    @DisplayName("evict 后重新查库，能读到刚改的授权")
    void evictForcesReload() {
        givenUser(2, List.of("STUDENT"), List.of("chat:send"));
        RbacSnapshotCache cache = new RbacSnapshotCache(roleMapper, permissionMapper);
        assertEquals(1, cache.get(2L).permissions().size());

        // 后台把该用户提成内容运营
        givenUser(2, List.of("CONTENT_OPERATOR"), List.of("kb:view", "kb:create"));
        cache.evict(2L);

        assertEquals(2, cache.get(2L).permissions().size());
        assertEquals(List.of("CONTENT_OPERATOR"), cache.get(2L).roles());
        verify(permissionMapper, times(2)).selectPermCodesByUserId(2L);
    }

    @Test
    @DisplayName("evictByRoleId：改角色授权后，角色下所有用户的快照都失效")
    void evictByRoleIdClearsItsUsers() {
        givenUser(3, List.of("DATA_AUDITOR"), List.of("kb:view"));
        when(roleMapper.selectUserIdsByRoleId(9L)).thenReturn(List.of(3L, 4L));
        RbacSnapshotCache cache = new RbacSnapshotCache(roleMapper, permissionMapper);
        cache.get(3L);
        cache.get(4L);

        cache.evictByRoleId(9L);
        cache.get(3L);
        cache.get(4L);

        verify(permissionMapper, times(2)).selectPermCodesByUserId(3L);
        verify(permissionMapper, times(2)).selectPermCodesByUserId(4L);
    }

    @Test
    @DisplayName("userId 为 null 返回空快照，不查库")
    void nullUserReturnsEmpty() {
        RbacSnapshotCache cache = new RbacSnapshotCache(roleMapper, permissionMapper);
        assertTrue(cache.get(null).permissions().isEmpty());
        assertTrue(cache.get(null).roles().isEmpty());
        verifyNoInteractions(roleMapper, permissionMapper);
    }

    @Test
    @DisplayName("TTL 过期后自动重查（ttl=0 时每次都查）")
    void reloadsAfterTtlExpired() {
        givenUser(5, List.of("STUDENT"), List.of("chat:send"));
        RbacSnapshotCache cache = new RbacSnapshotCache(roleMapper, permissionMapper, 0L);

        cache.get(5L);
        cache.get(5L);

        verify(permissionMapper, times(2)).selectPermCodesByUserId(5L);
    }

    @Test
    @DisplayName("快照不可变，防止调用方拿到后改掉缓存内容")
    void snapshotIsImmutable() {
        RbacSnapshotCache.Snapshot snapshot = new RbacSnapshotCache.Snapshot(
                List.of("STUDENT"), List.of("chat:send"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.permissions().add("kb:view"));
    }
}
