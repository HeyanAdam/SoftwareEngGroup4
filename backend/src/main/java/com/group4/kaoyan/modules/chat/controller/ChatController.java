package com.group4.kaoyan.modules.chat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.chat.stream.AiRequestContext;
import com.group4.kaoyan.modules.chat.stream.ChatAsyncService;
import com.group4.kaoyan.modules.chat.stream.ChatSseAdapter;
import com.group4.kaoyan.modules.chat.stream.ChatStreamBacklog;
import com.group4.kaoyan.modules.chat.stream.ChatStreamCommand;
import com.group4.kaoyan.modules.chat.stream.ChatStreamService;
import com.group4.kaoyan.modules.chat.stream.ChatStreamStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * AI 对话与答疑（对齐《接口设计文档 V1.1 适配版》6.1 / 6.2）。
 *
 * <p>路径与权限码按文档：</p>
 * <ul>
 *   <li>{@code GET  /api/v1/chat/conversations} —— 仅需登录，查本人会话；</li>
 *   <li>{@code GET  /api/v1/chat/conversations/{id}/messages} —— chat:send；</li>
 *   <li>{@code DELETE /api/v1/chat/conversations/{id}} —— chat:send；</li>
 *   <li>{@code POST /api/v1/chat/conversations/{id}/messages/stream} —— chat:send（SSE）。</li>
 * </ul>
 *
 * <p>A-1 起，HTTP 层只做两件事：把登录态翻译成 {@link AiRequestContext}、
 * 把统一事件流套上旧 SSE 适配器。业务编排全部在 {@link ChatStreamService}。</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;
    private final ChatSseAdapter chatSseAdapter;
    private final ChatAsyncService chatAsyncService;
    private final ChatStreamStore chatStreamStore;

    public record ChatReq(Long conversationId, Long kbId, String question, String clientMessageId) {
    }

    /** 文档口径：SSE 流式问答，会话 ID 在路径上。事件依次为 meta / references / delta / done */
    @SaCheckPermission("chat:send")
    @PostMapping(value = "/conversations/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long conversationId,
                             @RequestBody ChatReq req,
                             @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ChatReq body = req == null ? new ChatReq(null, null, null, null) : req;
        AiRequestContext context = contextOf(conversationId, body, requestId);
        return chatSseAdapter.adapt(chatStreamService.stream(context, body.question()));
    }

    /** 兼容旧口径：会话 ID 放请求体；conversationId 为空表示新建会话。 */
    @SaCheckPermission("chat:send")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLegacy(@RequestBody ChatReq req,
                                   @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ChatReq body = req == null ? new ChatReq(null, null, null, null) : req;
        AiRequestContext context = contextOf(body.conversationId(), body, requestId);
        return chatSseAdapter.adapt(chatStreamService.stream(context, body.question()));
    }

    @GetMapping("/conversations")
    public R<List<ChatConversation>> conversations() {
        return R.ok(chatService.list(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 异步问答（A-2）：同一事务写用户消息 + AI_CHAT 任务 + Outbox，立即返回 streamId。
     * 前端随后通过 WebSocket 或补发接口消费事件。
     */
    @SaCheckPermission("chat:send")
    @PostMapping("/conversations/{conversationId}/messages/async")
    public R<ChatAsyncService.ChatSubmission> async(@PathVariable Long conversationId,
                                                    @RequestBody ChatReq req,
                                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ChatReq body = req == null ? new ChatReq(null, null, null, null) : req;
        AiRequestContext context = contextOf(conversationId, body, requestId);
        return R.ok(chatAsyncService.submit(context, body.question()));
    }

    /** 兼容旧口径：会话 ID 放请求体。 */
    @SaCheckPermission("chat:send")
    @PostMapping("/messages/async")
    public R<ChatAsyncService.ChatSubmission> asyncLegacy(@RequestBody ChatReq req,
                                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ChatReq body = req == null ? new ChatReq(null, null, null, null) : req;
        AiRequestContext context = contextOf(body.conversationId(), body, requestId);
        return R.ok(chatAsyncService.submit(context, body.question()));
    }

    /**
     * 断线补发：返回 {@code lastSequence} 之后的所有事件；已完成的流也能查到尾部 done。
     */
    @SaCheckPermission("chat:send")
    @GetMapping("/streams/{streamId}/events")
    public R<ChatStreamBacklog> streamEvents(@PathVariable String streamId,
                                             @RequestParam(value = "lastSequence", defaultValue = "0") long lastSequence) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean admin = StpUtil.hasRole(RoleConstants.SYSTEM_ADMIN);
        return R.ok(chatStreamStore.backlog(streamId, Math.max(0, lastSequence), userId, admin));
    }

    /**
     * 会话历史消息。
     *
     * <p>不传分页参数时保持旧行为：返回全部消息数组（兼容既有调用）。
     * 传了 {@code before_id} 或 {@code limit} 时走增量分页，返回
     * {@code {items, has_more, next_before_id}}。</p>
     */
    @SaCheckPermission("chat:send")
    @GetMapping("/conversations/{id}/messages")
    public R<Object> messages(@PathVariable Long id,
                              @RequestParam(value = "before_id", required = false) Long beforeId,
                              @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (beforeId == null && limit == null) {
            return R.ok(chatService.messages(userId, id));
        }
        return R.ok(chatService.messagePage(userId, id, beforeId, limit));
    }

    @SaCheckPermission("chat:send")
    @DeleteMapping("/conversations/{id}")
    public R<Void> delete(@PathVariable Long id) {
        chatService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    private AiRequestContext contextOf(Long conversationId, ChatReq req, String requestId) {
        String traceId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        ChatStreamCommand command = new ChatStreamCommand(
                StpUtil.getLoginIdAsLong(), conversationId, req.kbId(), req.question(),
                traceId, req.clientMessageId());
        return AiRequestContext.fromHttp(command);
    }
}
