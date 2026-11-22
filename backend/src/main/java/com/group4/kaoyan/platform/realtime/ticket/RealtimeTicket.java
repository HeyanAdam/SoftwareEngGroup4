package com.group4.kaoyan.platform.realtime.ticket;

import java.util.List;

/**
 * 一次性票据的载荷，存 Redis 的 value（JSON）。
 *
 * @param userId          票据签发给谁
 * @param allowedChannels 允许访问的通道，目前只有 {@code chat}；后续任务进度/通知复用时再加
 * @param issuedAt        签发时间（毫秒）
 * @param expiresAt       过期时间（毫秒），TTL 由 Redis 兜底，这里再做一次防御性校验
 */
public record RealtimeTicket(
        Long userId,
        List<String> allowedChannels,
        long issuedAt,
        long expiresAt
) {

    public RealtimeTicket {
        allowedChannels = allowedChannels == null ? List.of() : List.copyOf(allowedChannels);
    }

    public boolean isExpired(long nowMillis) {
        return expiresAt > 0 && nowMillis >= expiresAt;
    }
}
