package com.group4.kaoyan.platform.realtime.session;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一条在线连接的可变状态。
 *
 * <p>和 {@link RealtimeConnectionRegistry} 里"按用户分组"的结构配合使用：
 * 会话对象本身不可变（userId / session / channels / 建连时间），只有最后活动时间是并发写的，
 * 所以用 {@link AtomicLong} 而不是把整个对象加锁。</p>
 */
public final class RealtimeConnection {

    private final Long userId;
    private final WebSocketSession session;
    private final Set<String> channels;
    private final Instant connectedAt;
    private final AtomicLong lastActivity;

    RealtimeConnection(Long userId, WebSocketSession session, List<String> channels, long lastActivityMillis) {
        this.userId = userId;
        this.session = session;
        this.channels = channels == null ? Set.of() : Set.copyOf(channels);
        this.connectedAt = Instant.now();
        this.lastActivity = new AtomicLong(lastActivityMillis);
    }

    public Long userId() {
        return userId;
    }

    public WebSocketSession session() {
        return session;
    }

    public String sessionId() {
        return session.getId();
    }

    public Set<String> channels() {
        return channels;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    public long lastActivity() {
        return lastActivity.get();
    }

    /** 收到任意一帧消息就刷新活动时间。 */
    public void touch(long nowMillis) {
        lastActivity.set(nowMillis);
    }

    public boolean isOpen() {
        return session.isOpen();
    }
}
