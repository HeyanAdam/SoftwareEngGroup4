package com.group4.kaoyan.modules.chat.stream;

import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.security.RoleConstants;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一次 AI 请求的完整上下文：会话归属、角色、知识库可见范围全部显式携带。
 *
 * <p>A-1 起，流式核心<b>不再读取 HTTP 线程里的 {@code StpUtil}</p>：
 * HTTP 入口只负责把登录态翻译成本对象，MQ 消费者 / 后台任务则从任务载荷重建，
 * 这样同一个核心既能跑在 Tomcat 线程，也能跑在 RabbitMQ 监听线程。</p>
 */
public record AiRequestContext(
        Long userId,
        Long conversationId,
        Long kbId,
        boolean adminScope,
        Set<String> roles,
        String requestId,
        String streamId,
        String clientMessageId) {

    public AiRequestContext {
        roles = roles == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(roles));
        requestId = blankToNull(requestId) == null ? UUID.randomUUID().toString() : requestId;
        streamId = blankToNull(streamId) == null ? UUID.randomUUID().toString() : streamId;
    }

    /** HTTP 入口专用：此处是唯一允许触碰 {@code StpUtil} 的地方。 */
    public static AiRequestContext fromHttp(ChatStreamCommand command) {
        Set<String> roles = new LinkedHashSet<>(StpUtil.getRoleList());
        return new AiRequestContext(
                command.userId(),
                command.conversationId(),
                command.kbId(),
                roles.contains(RoleConstants.SYSTEM_ADMIN),
                roles,
                command.requestId(),
                null,
                command.clientMessageId());
    }

    /** 后台任务专用：不依赖任何登录态，管理员视角来自任务载荷。 */
    public static AiRequestContext forBackground(Long userId,
                                                Long conversationId,
                                                Long kbId,
                                                boolean adminScope,
                                                Set<String> roles,
                                                String requestId,
                                                String streamId,
                                                String clientMessageId) {
        return new AiRequestContext(userId, conversationId, kbId, adminScope, roles,
                requestId, streamId, clientMessageId);
    }

    public boolean isAdmin() {
        return adminScope;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
