package com.group4.kaoyan.platform.realtime.heartbeat;

import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeErrorCode;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnection;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnectionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;

/**
 * 心跳与空闲清理（B-1 第 8 步）。
 *
 * <p>应用层 ping/pong 的意义：WebSocket 协议层虽然有 ping 帧，但浏览器 JS 发不了，
 * 而且经过代理时经常被吞。所以约定"客户端每 30 秒发一条 {@code type=ping} 的文本消息"，
 * 服务端回 pong 并刷新活动时间；90 秒没动静就由本类主动关闭，
 * 避免"页面已经睡了/网络已经断了但服务端还挂着 Session"。</p>
 *
 * <p>扫描是定时任务（默认 10 秒一轮），只关闭空闲超时的连接，不动在线的。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeHeartbeatMonitor {

    private final RealtimeConnectionRegistry registry;
    private final RealtimeProperties properties;

    /** 定时入口：{@code @EnableScheduling} 已在启动类开启。 */
    @Scheduled(fixedDelayString = "${kaoyan.realtime.idle-sweep-interval-ms:10000}")
    public void sweepIdleConnections() {
        sweep(System.currentTimeMillis());
    }

    /**
     * 扫一轮：关闭已断开和空闲超时的连接。
     *
     * @return 本轮关闭的连接数
     */
    int sweep(long nowMillis) {
        long idleMillis = properties.getIdleTimeout().toMillis();
        int closed = 0;
        for (RealtimeConnection connection : registry.allConnections()) {
            if (!connection.isOpen()) {
                registry.unregister(connection.userId(), connection.sessionId());
                continue;
            }
            if (nowMillis - connection.lastActivity() >= idleMillis) {
                closeIdle(connection, nowMillis - connection.lastActivity());
                closed++;
            }
        }
        return closed;
    }

    private void closeIdle(RealtimeConnection connection, long idleForMillis) {
        try {
            connection.session().close(new CloseStatus(
                    RealtimeErrorCode.IDLE_TIMEOUT.getCode(), "IDLE_TIMEOUT"));
            log.info("用户 {} 的连接 {} 空闲 {} ms，已主动关闭",
                    connection.userId(), connection.sessionId(), idleForMillis);
        } catch (IOException | IllegalStateException e) {
            log.debug("关闭空闲连接 {} 失败：{}", connection.sessionId(), e.getMessage());
        } finally {
            // 关闭帧可能失败，注册表必须自己清，不能等 afterConnectionClosed
            registry.unregister(connection.userId(), connection.sessionId());
        }
    }
}
