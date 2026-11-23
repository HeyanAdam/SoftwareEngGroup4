package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MQ 消费者专用生命周期：提前建 STREAMING 回答、逐事件写 Redis Stream、
 * 每 20 条或每秒刷一次数据库，最终收口 COMPLETED/FAILED。
 */
@Slf4j
@RequiredArgsConstructor
public class PersistentChatLifecycle implements ChatStreamLifecycle {

    private static final int DB_FLUSH_EVERY = 20;
    private static final long DB_FLUSH_INTERVAL_MS = 1000L;

    private final ChatService chatService;
    private final ChatStreamStore streamStore;
    private final Long userId;

    private ChatConversation conversation;
    private ChatMessage userMessage;
    private ChatMessage assistantMessage;
    private String streamId;

    private final StringBuilder answer = new StringBuilder();
    private long lastSequence;
    private long lastFlushAt = System.currentTimeMillis();
    private int eventCount;
    private String finalStatus = ChatStreamBacklog.STATUS_STREAMING;
    private String finalError;
    private boolean finalRetryable;

    @Override
    public void onStarted(ChatConversation conversation, ChatMessage userMessage, String streamId) {
        this.conversation = conversation;
        this.userMessage = userMessage;
        this.streamId = streamId;
        ChatMessage existing = chatService.findAssistantByStreamId(streamId);
        if (existing != null) {
            // MQ 重试：复用同一条回答，清空旧事件后从头累计序号
            streamStore.clear(streamId);
            this.assistantMessage = chatService.resetAssistantStreaming(existing.getId());
        } else {
            this.assistantMessage = chatService.createAssistantStreaming(
                    conversation, userId, streamId, userMessage == null ? null : userMessage.getId());
        }
        log.info("AI 回答进入 STREAMING streamId={} assistantMessageId={}",
                streamId, assistantMessage == null ? null : assistantMessage.getId());
    }

    @Override
    public void onEvent(ChatStreamEvent event) {
        streamStore.append(event, userId,
                conversation == null ? null : conversation.getId(),
                assistantMessage == null ? null : assistantMessage.getId());
        lastSequence = event.sequence();
        if (ChatStreamEvent.TYPE_DELTA.equals(event.type())) {
            answer.append(String.valueOf(event.data().getOrDefault("content", "")));
            eventCount++;
            maybeFlushProgress(false);
        } else if (ChatStreamEvent.TYPE_ERROR.equals(event.type())) {
            finalStatus = ChatStreamBacklog.STATUS_FAILED;
            finalError = String.valueOf(event.data().getOrDefault("message", "生成失败"));
            Object retryable = event.data().get("retryable");
            finalRetryable = Boolean.TRUE.equals(retryable)
                    || "true".equalsIgnoreCase(String.valueOf(retryable));
            maybeFlushProgress(true);
        } else if (ChatStreamEvent.TYPE_DONE.equals(event.type())) {
            finalStatus = ChatStreamBacklog.STATUS_COMPLETED;
            maybeFlushProgress(true);
        }
    }

    @Override
    public ChatMessage persistAssistant(ChatConversation conversation, Long userId, String content,
                                        String referencesJson, String modelName, String streamId,
                                        String status, String errorMessage, long lastSequence) {
        if (assistantMessage == null) {
            return null;
        }
        // 以核心的完整文本为准（取消时核心可能比事件累计多/少）
        String finalContent = content == null ? answer.toString() : content;
        this.finalStatus = status;
        if (status != null && ChatStreamBacklog.STATUS_FAILED.equals(status)) {
            this.finalError = errorMessage;
        }
        ChatMessage saved = chatService.finishAssistant(assistantMessage.getId(), finalContent,
                referencesJson, status, errorMessage, lastSequence);
        if (userMessage != null && userMessage.getId() != null) {
            chatService.markUserMessageCompleted(userMessage.getId());
        }
        streamStore.markStatus(streamId, status, lastSequence, errorMessage);
        log.info("AI 回答收口 streamId={} status={} lastSequence={} chars={}",
                streamId, status, lastSequence, finalContent.length());
        return saved;
    }

    public String status() {
        return finalStatus;
    }

    public String errorMessage() {
        return finalError;
    }

    /** 最近一次 error 事件是否声明可重试；供 MQ 消费者决定重投还是直接收口 */
    public boolean retryable() {
        return finalRetryable;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public Long assistantMessageId() {
        return assistantMessage == null ? null : assistantMessage.getId();
    }

    private void maybeFlushProgress(boolean force) {
        if (assistantMessage == null) {
            return;
        }
        if (!force && eventCount % DB_FLUSH_EVERY != 0
                && System.currentTimeMillis() - lastFlushAt < DB_FLUSH_INTERVAL_MS) {
            return;
        }
        try {
            chatService.updateAssistantProgress(assistantMessage.getId(), answer.toString(), lastSequence);
        } catch (Exception e) {
            log.warn("刷新回答进度失败（继续生成）streamId={}：{}", streamId, e.getMessage());
        }
        lastFlushAt = System.currentTimeMillis();
    }
}
