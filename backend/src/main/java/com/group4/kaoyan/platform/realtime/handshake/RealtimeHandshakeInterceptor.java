package com.group4.kaoyan.platform.realtime.handshake;

import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeErrorCode;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import com.group4.kaoyan.platform.realtime.session.RealtimeSessionAttributes;
import com.group4.kaoyan.platform.realtime.ticket.RealtimeTicket;
import com.group4.kaoyan.platform.realtime.ticket.RealtimeTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 握手鉴权（B-1 第 4、5 步）：连接建立的<b>唯一</b>入口关卡。
 *
 * <p>校验顺序：</p>
 * <ol>
 *   <li>URL 里必须带 {@code ?ticket=xxx}，且票据能成功消费（一次性，取出来即删）；</li>
 *   <li>Origin 必须在白名单里（防止别的站点拿用户浏览器偷偷建连）；</li>
 *   <li>账号状态正常（复用 {@link AccountStatusGuard}，被禁用直接拒绝）；</li>
 *   <li>拥有 {@code chat:send} 权限（复用 {@link RbacSnapshotCache} 快照，不额外查库）。</li>
 * </ol>
 *
 * <p>失败时一律用 HTTP 状态码拒绝握手（WebSocket 还没建立，发不了关闭帧），
 * 并在响应头 {@code X-Realtime-Error} 里放错误码，方便前端/测试工具排查。</p>
 *
 * <p>票据原文不写日志、不进 attributes，消费完就丢；attributes 里只放 userId 和通道。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    private final RealtimeTicketService ticketService;
    private final AccountStatusGuard accountStatusGuard;
    private final RbacSnapshotCache rbacSnapshotCache;
    private final RealtimeProperties properties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String ticket = queryParam(request, "ticket");
        Optional<RealtimeTicket> consumed = ticketService.consume(ticket);
        if (consumed.isEmpty()) {
            // 不区分"不存在/过期/重复使用"，统一回 401，避免泄露票据是否存在
            return reject(response, HttpStatus.UNAUTHORIZED, RealtimeErrorCode.UNAUTHORIZED);
        }

        RealtimeTicket payload = consumed.get();
        if (!originAllowed(request.getHeaders().getOrigin())) {
            log.warn("用户 {} 的 WebSocket 握手 Origin 不合法：{}",
                    payload.userId(), request.getHeaders().getOrigin());
            return reject(response, HttpStatus.FORBIDDEN, RealtimeErrorCode.FORBIDDEN);
        }

        try {
            accountStatusGuard.ensureActive(payload.userId());
        } catch (RuntimeException e) {
            log.info("用户 {} 账号状态异常，拒绝 WebSocket 握手：{}", payload.userId(), e.getMessage());
            return reject(response, HttpStatus.FORBIDDEN, RealtimeErrorCode.FORBIDDEN);
        }

        if (!payload.allowedChannels().contains(RealtimeProtocol.CHANNEL_CHAT)) {
            log.warn("用户 {} 的票据没有 chat 通道权限", payload.userId());
            return reject(response, HttpStatus.FORBIDDEN, RealtimeErrorCode.FORBIDDEN);
        }
        if (!rbacSnapshotCache.get(payload.userId()).permissions().contains(PermissionConstants.CHAT_SEND)) {
            log.info("用户 {} 缺少 chat:send 权限，拒绝 WebSocket 握手", payload.userId());
            return reject(response, HttpStatus.FORBIDDEN, RealtimeErrorCode.FORBIDDEN);
        }

        attributes.put(RealtimeSessionAttributes.USER_ID, payload.userId());
        attributes.put(RealtimeSessionAttributes.CHANNELS, payload.allowedChannels());
        attributes.put(RealtimeSessionAttributes.CONNECTED_AT, System.currentTimeMillis());
        log.info("用户 {} 通过票据鉴权，允许建立 WebSocket 连接", payload.userId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理：登记连接在 RealtimeChatHandler.afterConnectionEstablished 里做
    }

    /**
     * Origin 白名单判断：允许配置 {@code http://localhost:*} 这种端口通配；
     * 没有 Origin 头（Postman / 自动化测试客户端）视为合法，浏览器请求则必须匹配。
     */
    boolean originAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        List<String> allowed = properties.getAllowedOrigins();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        String target = stripTrailingSlash(origin.trim());
        for (String candidate : allowed) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String pattern = stripTrailingSlash(candidate.trim());
            if ("*".equals(pattern)) {
                return true;
            }
            if (pattern.equalsIgnoreCase(target)) {
                return true;
            }
            // 形如 http://localhost:*：去掉 * 后按前缀匹配端口
            if (pattern.endsWith(":*")
                    && target.toLowerCase().startsWith(pattern.substring(0, pattern.length() - 1).toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String queryParam(ServerHttpRequest request, String name) {
        try {
            return UriComponentsBuilder.fromUri(request.getURI()).build()
                    .getQueryParams().getFirst(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status, RealtimeErrorCode error) {
        response.setStatusCode(status);
        response.getHeaders().add("X-Realtime-Error", error.name());
        return false;
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
