package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.dto.ChatAnswerTaskDTO;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-2 验收：流内 error 事件是否触发 MQ 重试，由 handler 决定抛出还是直接收口。
 */
class ChatAnswerTaskHandlerTest {

    private ChatService chatService;
    private ChatStreamService streamService;
    private ChatStreamStore streamStore;
    private AsyncTaskService asyncTaskService;
    private ChatAnswerTaskHandler handler;

    private ChatAnswerTaskDTO task;
    private ChatConversation conversation;
    private ChatMessage userMessage;
    private ChatMessage assistant;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        streamService = mock(ChatStreamService.class);
        streamStore = mock(ChatStreamStore.class);
        asyncTaskService = mock(AsyncTaskService.class);
        handler = new ChatAnswerTaskHandler(chatService, streamService, streamStore, asyncTaskService);

        task = new ChatAnswerTaskDTO();
        task.setUserId(7L);
        task.setConversationId(10L);
        task.setUserMessageId(100L);
        task.setStreamId("stream-1");
        task.setQuestion("问题");
        task.setRoles(Set.of("STUDENT"));

        conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        userMessage = new ChatMessage();
        userMessage.setId(100L);
        userMessage.setUserId(7L);
        userMessage.setConversationId(10L);
        assistant = new ChatMessage();
        assistant.setId(200L);

        when(chatService.findOwned(7L, 10L)).thenReturn(conversation);
        when(chatService.findMessage(100L)).thenReturn(userMessage);
        when(chatService.findAssistantByStreamId("stream-1")).thenReturn(null);
        when(chatService.createAssistantStreaming(any(), eq(7L), eq("stream-1"), eq(100L)))
                .thenReturn(assistant);
    }

    @Test
    @DisplayName("可重试的 LLM 错误抛异常，交给消费者延迟重投")
    void shouldThrowForRetryableError() {
        mockStreamError(true);

        assertThrows(ChatAnswerTaskHandler.RetryableChatException.class,
                () -> handler.handle(task, 1L));
        verify(asyncTaskService, never()).fail(anyLong(), anyString());
    }

    @Test
    @DisplayName("不可重试错误直接置 FAILED 并正常 ack")
    void shouldFailWithoutRetryForNonRetryableError() {
        mockStreamError(false);

        assertDoesNotThrow(() -> handler.handle(task, 1L));
        verify(asyncTaskService).fail(1L, "上游拒绝");
        verify(asyncTaskService, never()).succeed(anyLong(), any());
    }

    private void mockStreamError(boolean retryable) {
        when(streamService.stream(any(), anyString(), any(PersistentChatLifecycle.class)))
                .thenAnswer(invocation -> {
                    PersistentChatLifecycle lifecycle = invocation.getArgument(2);
                    lifecycle.onStarted(conversation, userMessage, "stream-1");
                    lifecycle.onEvent(ChatStreamEvent.of("error", "req", "stream-1", 2,
                            Map.of("code", "LLM_UPSTREAM_ERROR", "message", "上游拒绝",
                                    "retryable", retryable)));
                    return Flux.empty();
                });
    }
}
