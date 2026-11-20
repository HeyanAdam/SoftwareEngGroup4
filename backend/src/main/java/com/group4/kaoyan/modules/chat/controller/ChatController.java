package com.group4.kaoyan.modules.chat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.chat.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

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
 * <p>兼容：{@code POST /api/v1/chat/stream} 保留为旧口径别名（会话 ID 走请求体），
 * 前端 P3 一律使用文档里的 {@code /messages/stream}。</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final RagChatService ragChatService;

    public record ChatReq(Long conversationId, Long kbId, String question) {
    }

    /** 文档口径：SSE 流式问答，会话 ID 在路径上。事件依次为 meta / references / delta / done */
    @SaCheckPermission("chat:send")
    @PostMapping(value = "/conversations/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long conversationId, @RequestBody ChatReq req) {
        Long kbId = req == null ? null : req.kbId();
        String question = req == null ? null : req.question();
        return ragChatService.chat(StpUtil.getLoginIdAsLong(), conversationId, kbId, question);
    }

    /** 兼容旧口径：会话 ID 放请求体；conversationId 为空表示新建会话。 */
    @SaCheckPermission("chat:send")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLegacy(@RequestBody ChatReq req) {
        return ragChatService.chat(StpUtil.getLoginIdAsLong(),
                req.conversationId(), req.kbId(), req.question());
    }

    @GetMapping("/conversations")
    public R<List<ChatConversation>> conversations() {
        return R.ok(chatService.list(StpUtil.getLoginIdAsLong()));
    }

    @SaCheckPermission("chat:send")
    @GetMapping("/conversations/{id}/messages")
    public R<List<ChatMessage>> messages(@PathVariable Long id) {
        return R.ok(chatService.messages(StpUtil.getLoginIdAsLong(), id));
    }

    @SaCheckPermission("chat:send")
    @DeleteMapping("/conversations/{id}")
    public R<Void> delete(@PathVariable Long id) {
        chatService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }
}
