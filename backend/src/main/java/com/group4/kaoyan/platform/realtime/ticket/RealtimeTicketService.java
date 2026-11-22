package com.group4.kaoyan.platform.realtime.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 一次性票据的签发与消费（B-1 第 3、4 步）。
 *
 * <p>为什么要有票据，而不是握手时直接带登录 Token：Token 放在 URL 里会进浏览器历史、
 * 进 Nginx access log、进 Referer，等于长期凭证到处留痕。票据只活 60 秒、且只能换一次连接，
 * 泄露了也基本没用。</p>
 *
 * <p>Redis 键：{@code realtime:ticket:{ticket}}，value 是 {@link RealtimeTicket} 的 JSON，
 * TTL 60 秒；消费用 {@code GETDEL}（Spring Data Redis 的 {@code getAndDelete}）保证
 * "取出来 + 删掉"是一个原子操作，两个并发握手不可能拿到同一张票。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeTicketService {

    /** Redis 键前缀，和 auth 的 {@code auth:captcha:} 一样按模块分区，方便排查。 */
    public static final String KEY_PREFIX = "realtime:ticket:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;

    /** 给聊天通道签票（当前唯一入口）。 */
    public IssuedTicket issueChatTicket(Long userId) {
        return issue(userId, List.of(RealtimeProtocol.CHANNEL_CHAT));
    }

    /**
     * 签发一张票据。
     *
     * @param userId  登录用户主键
     * @param channels 允许的通道
     */
    public IssuedTicket issue(Long userId, List<String> channels) {
        if (userId == null) {
            throw new IllegalArgumentException("票据必须绑定用户");
        }
        long now = System.currentTimeMillis();
        long ttlSeconds = properties.getTicketTtl().toSeconds();
        String ticket = "rt_" + UUID.randomUUID().toString().replace("-", "");
        RealtimeTicket payload = new RealtimeTicket(
                userId, channels, now, now + properties.getTicketTtl().toMillis());
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket,
                    objectMapper.writeValueAsString(payload), properties.getTicketTtl());
        } catch (Exception e) {
            // 序列化失败属于部署/代码问题，不该把票据发出去
            throw new IllegalStateException("票据写入 Redis 失败", e);
        }
        // 只记录用户，不记录票据原文：日志里出现票据等于把一次性凭证又泄露一遍
        log.info("已为用户 {} 签发 WebSocket 票据，有效期 {} 秒", userId, ttlSeconds);
        return new IssuedTicket(ticket, ttlSeconds);
    }

    /**
     * 消费一张票据：取出来就删，返回空表示无效 / 过期 / 已被用掉。
     *
     * <p>注意：消费失败的原因（不存在、过期、重复使用）对客户端统一回"票据无效"，
     * 不区分——否则等于告诉攻击者"这张票确实存在过"。</p>
     */
    public Optional<RealtimeTicket> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        String key = KEY_PREFIX + ticket.trim();
        String raw;
        try {
            raw = redisTemplate.opsForValue().getAndDelete(key);
        } catch (RuntimeException e) {
            log.warn("读取 WebSocket 票据失败，按无效票据处理：{}", e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            RealtimeTicket payload = objectMapper.readValue(raw, RealtimeTicket.class);
            if (payload == null || payload.userId() == null || payload.isExpired(System.currentTimeMillis())) {
                return Optional.empty();
            }
            return Optional.of(payload);
        } catch (Exception e) {
            // 同样不打印 raw（raw 里可能包含票据本身）
            log.warn("WebSocket 票据内容解析失败，按无效票据处理");
            return Optional.empty();
        }
    }

    /** 签发结果：票据原文 + 有效期（秒），直接对应接口返回体。 */
    public record IssuedTicket(String ticket, long expiresInSeconds) {
    }
}
