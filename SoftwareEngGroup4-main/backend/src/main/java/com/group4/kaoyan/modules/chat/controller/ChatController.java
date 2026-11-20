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

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final RagChatService ragChatService;

    public record ChatReq(Long conversationId, Long kbId, String question) {
    }

    /** 流式问答：text/event-stream，事件依次为 meta / references / delta / done */
    @SaCheckPermission("chat:send")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatReq req) {
        return ragChatService.chat(StpUtil.getLoginIdAsLong(),
                req.conversationId(), req.kbId(), req.question());
    }

    @GetMapping("/conversations")
    public R<List<ChatConversation>> conversations() {
        return R.ok(chatService.list(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/conversations/{id}/messages")
    public R<List<ChatMessage>> messages(@PathVariable Long id) {
        return R.ok(chatService.messages(StpUtil.getLoginIdAsLong(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public R<Void> delete(@PathVariable Long id) {
        chatService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }
}
