package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.common.security.RbacSnapshotCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P2 任务 2：StpInterfaceImpl 的守卫测试。
 *
 * <p>不连数据库、不起 Spring 容器，靠 Mockito 打桩 {@link RbacSnapshotCache}，
 * 验证"权限/角色怎么取、取不到时怎么兜底"。</p>
 */
@DisplayName("P2-StpInterface 权限数据源")
class StpInterfaceImplTest {

    private final RbacSnapshotCache cache = mock(RbacSnapshotCache.class);
    private final StpInterfaceImpl stpInterface = new StpInterfaceImpl(cache);

    @Test
    @DisplayName("学生账号只拿到 chat:send 一条权限")
    void studentGetsOnlyChatSend() {
        when(cache.get(1L)).thenReturn(new RbacSnapshotCache.Snapshot(
                List.of("STUDENT"), List.of("chat:send")));

        List<String> perms = stpInterface.getPermissionList(1L, "login");
        assertEquals(List.of("chat:send"), perms);
        assertEquals(1, perms.size(), "学生不应拿到第二条权限");

        List<String> roles = stpInterface.getRoleList(1L, "login");
        assertEquals(List.of("STUDENT"), roles);
    }

    @Test
    @DisplayName("loginId 是字符串（Sa-Token 会话持久化后常见）也能正确解析")
    void parsesStringLoginId() {
        when(cache.get(42L)).thenReturn(new RbacSnapshotCache.Snapshot(
                List.of("CONTENT_OPERATOR"),
                List.of("kb:view", "kb:create", "kb:delete",
                        "doc:upload", "doc:delete", "admin:vector:manage")));

        List<String> perms = stpInterface.getPermissionList(" 42 ", "login");
        assertEquals(6, perms.size(), "内容运营应为 6 条权限");
        assertTrue(perms.contains("kb:view") && perms.contains("admin:vector:manage"));
    }

    @Test
    @DisplayName("loginId 为 null 时不查库，直接返回空权限")
    void nullLoginIdReturnsEmpty() {
        assertTrue(stpInterface.getPermissionList(null, "login").isEmpty());
        assertTrue(stpInterface.getRoleList(null, "login").isEmpty());
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("loginId 不是数字（例如误用 username 当 loginId）返回空权限，不抛异常")
    void nonNumericLoginIdReturnsEmpty() {
        assertTrue(stpInterface.getPermissionList("zhangsan", "login").isEmpty(),
                "username 当 loginId 时应当查不到权限，提示改代码而不是全站 500");
        verify(cache, never()).get(anyLong());
    }

    @Test
    @DisplayName("查库异常时 fail-closed：返回空权限，绝不放行")
    void exceptionDegradesToNoPermission() {
        when(cache.get(7L)).thenThrow(new RuntimeException("db down"));

        assertTrue(stpInterface.getPermissionList(7L, "login").isEmpty());
        assertTrue(stpInterface.getRoleList(7L, "login").isEmpty());
    }

    @Test
    @DisplayName("parseUserId：Long / Integer / String / 非法值")
    void parseUserIdHandlesAllShapes() {
        assertEquals(9L, stpInterface.parseUserId(9L));
        assertEquals(9L, stpInterface.parseUserId(9));
        assertEquals(9L, stpInterface.parseUserId("9"));
        assertEquals(null, stpInterface.parseUserId(null));
        assertEquals(null, stpInterface.parseUserId("  "));
        assertEquals(null, stpInterface.parseUserId("abc"));
    }
}
