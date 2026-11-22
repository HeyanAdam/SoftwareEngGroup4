package com.group4.kaoyan.platform.realtime.session;

import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线连接注册表（B-1 第 6 步）：{@code userId -> Set<WebSocketSession>}。
 *
 * <p>解决三个实际问题：</p>
 * <ol>
 *   <li><b>多标签页/多设备</b>：同一个人开三个标签页就是三条 Session，都要能收消息，
 *       所以不能用一个 {@code Map<Long, WebSocketSession>} 把前一条覆盖掉；</li>
 *   <li><b>定向推送</b>：既能按用户推给"他所有在线端"，也能只推给某一个连接（B-2 断线时只取消该连接）；</li>
 *   <li><b>不泄漏</b>：连接关闭、发送失败、空闲超时三条路径都会摘掉记录，关闭的 Session 不会越积越多。</li>
 * </ol>
 *
 * <p>并发说明：外层用户表和内层连接表都是 {@code ConcurrentHashMap}；
 * "判上限 + 放入"在 {@code compute} 的原子块里做，避免两个标签页同时握手时都判成"没超限"。</p>
 */
@Slf4j
@Component
public class RealtimeConnectionRegistry {

    /** 用户主键 → （Session ID → 连接）。 */
    private final Map<Long, Map<String, RealtimeConnection>> byUser = new ConcurrentHashMap<>();

    /** Session ID → 连接，O(1) 反查，心跳和文本消息只拿到 session 时用。 */
    private final Map<String, RealtimeConnection> bySessionId = new ConcurrentHashMap<>();

    private final int maxConnectionsPerUser;

    @Autowired
    public RealtimeConnectionRegistry(RealtimeProperties properties) {
        this(Math.max(1, properties.getMaxConnectionsPerUser()));
    }

    /** 供单测注入上限，避免为了测"超限"去改全局配置。 */
    public RealtimeConnectionRegistry(int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    /**
     * 登记一条新连接。
     *
     * @return true 登记成功；false 表示该用户在线连接数已达上限（调用方应关闭连接并回 4429）
     */
    public boolean register(Long userId, WebSocketSession session, List<String> channels) {
        if (userId == null || session == null || session.getId() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        RealtimeConnection connection = new RealtimeConnection(userId, session, channels, now);
        boolean[] accepted = {false};

        byUser.compute(userId, (key, existing) -> {
            Map<String, RealtimeConnection> sessions = existing == null
                    ? new ConcurrentHashMap<>()
                    : existing;
            // 同一 Session ID 重复登记（例如重连后容器复用 ID）：覆盖即可，不算新增
            boolean sameConnection = sessions.containsKey(session.getId());
            if (!sameConnection && sessions.size() >= maxConnectionsPerUser) {
                // 满了就原样返回；existing 为 null 时返回 null，不留下空桶
                return existing;
            }
            sessions.put(session.getId(), connection);
            accepted[0] = true;
            return sessions;
        });

        if (accepted[0]) {
            bySessionId.put(session.getId(), connection);
        } else {
            log.info("用户 {} 在线连接数已达上限 {}，拒绝新连接 {}", userId, maxConnectionsPerUser, session.getId());
        }
        return accepted[0];
    }

    /** 连接关闭时按用户 + Session 摘除；返回是否真的移除了记录。 */
    public boolean unregister(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return false;
        }
        boolean[] removed = {false};
        byUser.computeIfPresent(userId, (key, sessions) -> {
            removed[0] = sessions.remove(sessionId) != null;
            return sessions.isEmpty() ? null : sessions;
        });
        if (removed[0]) {
            bySessionId.remove(sessionId);
        }
        return removed[0];
    }

    /** 只拿得到 Session ID 时的兜底摘除（异常关闭路径用）。 */
    public boolean unregisterBySessionId(String sessionId) {
        RealtimeConnection connection = sessionId == null ? null : bySessionId.get(sessionId);
        return connection != null && unregister(connection.userId(), sessionId);
    }

    /** 刷新最后活动时间（收到任意合法帧时调用）。 */
    public void touch(String sessionId) {
        touch(sessionId, System.currentTimeMillis());
    }

    void touch(String sessionId, long nowMillis) {
        RealtimeConnection connection = sessionId == null ? null : bySessionId.get(sessionId);
        if (connection != null) {
            connection.touch(nowMillis);
        }
    }

    /** 某个用户的全部在线连接（按连接建立顺序无保证，前端不要依赖顺序）。 */
    public List<RealtimeConnection> connectionsOf(Long userId) {
        Map<String, RealtimeConnection> sessions = userId == null ? null : byUser.get(userId);
        return sessions == null ? List.of() : new ArrayList<>(sessions.values());
    }

    /** 全量快照，给心跳扫描用。 */
    public Collection<RealtimeConnection> allConnections() {
        return new ArrayList<>(bySessionId.values());
    }

    public RealtimeConnection find(Long userId, String sessionId) {
        RealtimeConnection connection = sessionId == null ? null : bySessionId.get(sessionId);
        return connection != null && connection.userId().equals(userId) ? connection : null;
    }

    public RealtimeConnection findBySessionId(String sessionId) {
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    public int connectionCount(Long userId) {
        Map<String, RealtimeConnection> sessions = userId == null ? null : byUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    public int totalConnections() {
        return bySessionId.size();
    }

    /** 清掉已经关闭但没走到 {@code afterConnectionClosed} 的记录（防御性兜底）。 */
    public int pruneClosed() {
        int removed = 0;
        for (RealtimeConnection connection : allConnections()) {
            if (!connection.isOpen() && unregister(connection.userId(), connection.sessionId())) {
                removed++;
            }
        }
        return removed;
    }
}
