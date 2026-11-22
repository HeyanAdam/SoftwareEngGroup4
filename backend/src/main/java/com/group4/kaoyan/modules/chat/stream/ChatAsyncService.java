package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.dto.ChatAnswerTaskDTO;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.platform.messaging.entity.AsyncTask;
import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 异步问答提交（A-2）：用户消息 + AI_CHAT 任务 + Outbox 同一事务落库，随后立即返回。
 *
 * <p>幂等口径：重复的 {@code (conversationId,userId,clientMessageId)} 只会命中同一条用户消息；
 * 若该消息已有任务，直接复用原 {@code streamId/taskId}，不会再建任务或重复扣模型额度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAsyncService {

    private final ChatService chatService;
    private final AsyncTaskService asyncTaskService;

    public record ChatSubmission(Long taskId, String streamId, Long conversationId,
                                 Long userMessageId, boolean reused) {
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatSubmission submit(AiRequestContext context, String question) {
        if (question == null || question.isBlank()) {
            throw new BizException(400, "问题不能为空");
        }
        if (context.clientMessageId() == null || context.clientMessageId().isBlank()) {
            throw new BizException(400, "clientMessageId 不能为空");
        }
        ChatConversation conversation = context.conversationId() != null
                ? chatService.findOwned(context.userId(), context.conversationId())
                : chatService.createConversation(context.userId(), context.kbId(), question);

        ChatMessage userMessage = chatService.saveUserMessage(conversation, context.userId(),
                question, context.clientMessageId(), ChatMessage.STATUS_PENDING);

        AsyncTask existingTask = asyncTaskService.findChatTask(userMessage.getId());
        if (existingTask != null) {
            String existingStreamId = extractStreamId(existingTask.getPayloadJson());
            log.info("重复 clientMessageId 命中已有任务，直接复用 taskId={} streamId={}",
                    existingTask.getId(), existingStreamId);
            return new ChatSubmission(existingTask.getId(),
                    existingStreamId == null ? context.streamId() : existingStreamId,
                    conversation.getId(), userMessage.getId(), true);
        }

        String streamId = context.streamId() == null || context.streamId().isBlank()
                ? UUID.randomUUID().toString()
                : context.streamId();
        ChatAnswerTaskDTO message = new ChatAnswerTaskDTO();
        message.setUserId(context.userId());
        message.setConversationId(conversation.getId());
        message.setKbId(context.kbId() != null ? context.kbId() : conversation.getKbId());
        message.setQuestion(question);
        message.setRequestId(context.requestId());
        message.setStreamId(streamId);
        message.setClientMessageId(context.clientMessageId());
        message.setAdminScope(context.isAdmin());
        message.setRoles(context.roles() == null ? Set.of() : Set.copyOf(context.roles()));
        message.setUserMessageId(userMessage.getId());

        AsyncTask task = asyncTaskService.createAiChatTask(message);
        return new ChatSubmission(task.getId(), streamId, conversation.getId(),
                userMessage.getId(), false);
    }

    /** 任务载荷可能是 DTO，也可能是 MyBatis JSON 反序列化后的 Map，这里统一取 streamId。 */
    @SuppressWarnings("unchecked")
    private String extractStreamId(Object payload) {
        if (payload instanceof ChatAnswerTaskDTO dto) {
            return dto.getStreamId();
        }
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get("streamId");
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
