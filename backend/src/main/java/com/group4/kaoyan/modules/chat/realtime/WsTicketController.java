package com.group4.kaoyan.modules.chat.realtime;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.ticket.RealtimeTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 票据接口（B-1 第 3 步）：{@code POST /api/v1/chat/ws-ticket}。
 *
 * <p>独立成一个 Controller，不去动现有的 {@code ChatController}（SSE 那条链路正在用），
 * 两个 PR 合并时不会互相冲突。</p>
 *
 * <p>返回体沿用项目统一的 {@code R} 包装，{@code data} 里就是协议约定的三个字段：
 * {@code ticket}（60 秒、一次性）、{@code expiresIn}（秒）、{@code wsUrl}（连接路径，
 * 客户端自己拼 {@code wss://host/ws/chat?ticket=xxx}，不同环境域名不一样，所以不返回绝对地址）。</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class WsTicketController {

    private final RealtimeTicketService ticketService;
    private final RealtimeProperties realtimeProperties;

    @SaCheckPermission(PermissionConstants.CHAT_SEND)
    @PostMapping("/ws-ticket")
    public R<WsTicketVO> issue() {
        RealtimeTicketService.IssuedTicket issued =
                ticketService.issueChatTicket(SecurityUtils.currentUserId());
        return R.ok(new WsTicketVO(issued.ticket(), issued.expiresInSeconds(),
                realtimeProperties.getWsPath()));
    }

    /** 票据响应：字段名与协议文档一致，蛇形/驼峰由前端既有约定决定。 */
    public record WsTicketVO(String ticket, long expiresIn, String wsUrl) {
    }
}
