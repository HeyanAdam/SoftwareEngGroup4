package com.group4.kaoyan.platform.realtime.config;

import com.group4.kaoyan.platform.realtime.handshake.RealtimeHandshakeInterceptor;
import com.group4.kaoyan.platform.realtime.handler.RealtimeChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册（B-1 第 2 步）。
 *
 * <p>刻意做成<b>独立配置类</b>，不动现有的 {@code WebConfig}（那是 HTTP MVC 的拦截器和 CORS），
 * 避免"加个 WebSocket 把 REST 鉴权链改坏"这类事故。</p>
 *
 * <p>Origin 策略分两层：容器层 {@code setAllowedOriginPatterns("*")} 只负责让握手进入我们的拦截器，
 * 真正的白名单判断在 {@link RealtimeHandshakeInterceptor#originAllowed(String)} 里做，
 * 这样开发环境多端口、生产环境收紧都只改一处配置。</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final RealtimeChatHandler chatHandler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;
    private final RealtimeProperties properties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, properties.getWsPath())
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
