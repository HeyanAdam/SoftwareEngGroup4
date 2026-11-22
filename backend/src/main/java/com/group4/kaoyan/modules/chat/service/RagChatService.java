package com.group4.kaoyan.modules.chat.service;

import com.group4.kaoyan.modules.chat.stream.AiRequestContext;
import com.group4.kaoyan.modules.chat.stream.ChatSseAdapter;
import com.group4.kaoyan.modules.chat.stream.ChatStreamCommand;
import com.group4.kaoyan.modules.chat.stream.ChatStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 旧入口兼容壳（A-1 起不再承载业务逻辑）。
 *
 * @deprecated 新代码请注入 {@link ChatStreamService}（统一事件流）或
 * {@link ChatSseAdapter}（SSE 兼容）。本类只为历史调用方保留签名。
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final ChatStreamService chatStreamService;
    private final ChatSseAdapter chatSseAdapter;

    public SseEmitter chat(Long userId, Long conversationId, Long kbId, String question) {
        return chat(userId, conversationId, kbId, question, null, null);
    }

    public SseEmitter chat(Long userId, Long conversationId, Long kbId, String question,
                           String requestId, String clientMessageId) {
        ChatStreamCommand command = new ChatStreamCommand(
                userId, conversationId, kbId, question, requestId, clientMessageId);
        AiRequestContext context = AiRequestContext.fromHttp(command);
        return chatSseAdapter.adapt(chatStreamService.stream(context, question));
    }
}
