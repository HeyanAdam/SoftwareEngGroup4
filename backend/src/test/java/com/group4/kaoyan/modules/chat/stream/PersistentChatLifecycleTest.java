package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-2 验收：提前建 STREAMING、逐事件写 Redis、定期刷库、终态收口。
 */
class PersistentChatLifecycleTest {

    private ChatService chatService;
    private ChatStreamStore store;
    private PersistentChatLifecycle lifecycle;

    private ChatConversation conversation;
    private ChatMessage userMessage;
    private ChatMessage assistant;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        store = mock(ChatStreamStore.class);
        lifecycle = new PersistentChatLifecycle(chatService, store, 7L);

        conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        userMessage = new ChatMessage();
        userMessage.setId(100L);
        userMessage.setRole("user");
        assistant = new ChatMessage();
        assistant.setId(200L);
        assistant.setRole("assistant");

        when(chatService.findAssistantByStreamId("stream-1")).thenReturn(null);
        when(chatService.createAssistantStreaming(any(), eq(7L), eq("stream-1"), eq(100L)))
                .thenReturn(assistant);
        when(chatService.finishAssistant(anyLong(), anyString(), anyString(), anyString(), any(), anyLong()))
                .thenReturn(assistant);
    }

    @Test
    @DisplayName("onStarted 先建 STREAMING 回答，replyTo 指向用户消息")
    void shouldCreateStreamingAssistant() {
        lifecycle.onStarted(conversation, userMessage, "stream-1");

        verify(chatService).createAssistantStreaming(conversation, 7L, "stream-1", 100L);
        assertEquals(200L, lifecycle.assistantMessageId());
    }

    @Test
    @DisplayName("每 20 个 delta 至少刷一次数据库，Redis 每个事件都写")
    void shouldFlushProgressEveryTwentyEvents() {
        lifecycle.onStarted(conversation, userMessage, "stream-1");
        for (int i = 1; i <= 20; i++) {
            lifecycle.onEvent(ChatStreamEvent.of("delta", "req", "stream-1", i,
                    Map.of("content", "字")));
        }

        verify(store, times(20)).append(any(), anyLong(), anyLong(), anyLong());
        verify(chatService, times(1)).updateAssistantProgress(eq(200L), anyString(), eq(20L));
    }

    @Test
    @DisplayName("COMPLETED 收口：写 answer、markUserCompleted、Redis 状态转 COMPLETED")
    void shouldFinishCompleted() {
        lifecycle.onStarted(conversation, userMessage, "stream-1");
        lifecycle.onEvent(ChatStreamEvent.of("delta", "req", "stream-1", 1, Map.of("content", "回答")));

        lifecycle.persistAssistant(conversation, 7L, "回答", "[]", "deepseek-chat",
                "stream-1", "COMPLETED", null, 1);

        verify(chatService).finishAssistant(eq(200L), eq("回答"), eq("[]"), eq("COMPLETED"), isNull(), eq(1L));
        verify(chatService).markUserMessageCompleted(100L);
        verify(store).markStatus("stream-1", "COMPLETED", 1L, null);
        assertEquals("COMPLETED", lifecycle.status());
    }

    @Test
    @DisplayName("错误事件把最终状态标记为 FAILED 并携带原因")
    void shouldMarkFailed() {
        lifecycle.onStarted(conversation, userMessage, "stream-1");
        lifecycle.onEvent(ChatStreamEvent.of("error", "req", "stream-1", 2,
                Map.of("code", "LLM_TIMEOUT", "message", "模型超时")));

        lifecycle.persistAssistant(conversation, 7L, "", "[]", "deepseek-chat",
                "stream-1", "FAILED", "模型超时", 2);

        assertEquals("FAILED", lifecycle.status());
        assertEquals("模型超时", lifecycle.errorMessage());
        verify(store).markStatus("stream-1", "FAILED", 2L, "模型超时");
    }
}
