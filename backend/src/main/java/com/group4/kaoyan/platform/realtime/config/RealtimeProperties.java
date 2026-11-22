package com.group4.kaoyan.platform.realtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 连接层的可调参数（B-1）。
 *
 * <p>全部有默认值，因此不写配置也能跑；生产环境按需在 {@code deploy/.env} 里覆盖。
 * 前缀 {@code kaoyan.realtime}，与已有的 {@code kaoyan.auth}、{@code kaoyan.rag} 保持同一命名口径。</p>
 */
@Component
@ConfigurationProperties(prefix = "kaoyan.realtime")
@Getter
@Setter
public class RealtimeProperties {

    /** 连接路径：握手地址为 {@code /ws/chat?ticket=xxx}。 */
    private String wsPath = "/ws/chat";

    /** 票据有效期。协议要求 60 秒，票据只能消费一次。 */
    private Duration ticketTtl = Duration.ofSeconds(60);

    /** 心跳周期（客户端每 30 秒发一次 ping）。 */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    /** 空闲超时：超过这个时间没有任何消息就断开。 */
    private Duration idleTimeout = Duration.ofSeconds(90);

    /** 单条消息体上限（字节）。 */
    private int maxMessageBytes = 32 * 1024;

    /** 单条 chat.send 的正文长度上限（字符）。 */
    private int maxTextLength = 4000;

    /** 同一用户允许同时在线的连接数（多标签页 + 多设备）。 */
    private int maxConnectionsPerUser = 6;

    /** 单条字段（requestId / clientMessageId / type）的最大长度。 */
    private int maxFieldLength = 64;

    /**
     * 允许的 Origin（前端域名）。默认只放本机 Vite 开发服；
     * 部署到线上时把实际域名加进来，不支持通配时请逐个列全。
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    ));

    /** 空闲扫描间隔：默认 10 秒扫一次，不需要很精确。 */
    private long idleSweepIntervalMs = 10_000L;
}
