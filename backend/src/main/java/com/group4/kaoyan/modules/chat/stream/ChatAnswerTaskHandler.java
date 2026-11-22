package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.dto.ChatAnswerTaskDTO;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天回答任务执行器：在 MQ 监听线程里跑 {@link ChatStreamService}，
 * 逐事件写 Redis Stream，并更新数据库状态。
 *
 * <p>这里没有 {@code StpUtil}：上下文全部来自消息载荷，断线后任务仍能继续。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAnswerTaskHandler {

    private static final Duration MAX_DURATION = Duration.ofMinutes(10);

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;
    private final ChatStreamStore streamStore;
    private final AsyncTaskService asyncTaskService;

    public void handle(ChatAnswerTaskDTO task, Long taskId) {
        if (task == null || task.getUserId() == null || task.getConversationId() == null) {
            throw new BizException(400, "聊天任务载荷不完整");
        }
        ChatConversation conversation = chatService.findOwned(task.getUserId(), task.getConversationId());
        ChatMessage userMessage = chatService.findMessage(task.getUserMessageId());
        if (userMessage == null || !task.getUserId().equals(userMessage.getUserId())
                || !conversation.getId().equals(userMessage.getConversationId())) {
            throw new BizException(404, "用户消息不存在或不属于该会话");
        }

        AiRequestContext context = AiRequestContext.forBackground(
                task.getUserId(), conversation.getId(), task.getKbId(), task.isAdminScope(),
                task.getRoles(), task.getRequestId(), task.getStreamId(), task.getClientMessageId());

        PersistentChatLifecycle lifecycle = new PersistentChatLifecycle(chatService, streamStore, task.getUserId());
        chatStreamService.stream(context, task.getQuestion(), lifecycle)
                .blockLast(MAX_DURATION);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("streamId", task.getStreamId());
        result.put("userMessageId", task.getUserMessageId());
        result.put("assistantMessageId", lifecycle.assistantMessageId());
        result.put("status", lifecycle.status());
        result.put("lastSequence", lifecycle.lastSequence());

        if (ChatStreamBacklog.STATUS_CANCELLED.equals(lifecycle.status())) {
            asyncTaskService.cancel(taskId);
            log.info("AI 回答任务已取消 streamId={}", task.getStreamId());
        } else if (ChatStreamBacklog.STATUS_FAILED.equals(lifecycle.status())) {
            asyncTaskService.fail(taskId, lifecycle.errorMessage());
            log.warn("AI 回答任务失败 streamId={} error={}", task.getStreamId(), lifecycle.errorMessage());
        } else {
            asyncTaskService.succeed(taskId, result);
        }
    }
}
