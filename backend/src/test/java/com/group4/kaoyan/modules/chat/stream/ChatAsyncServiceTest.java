package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.dto.ChatAnswerTaskDTO;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.platform.messaging.entity.AsyncTask;
import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-2 验收：重复 clientMessageId 只建一条问题、一条任务。
 */
class ChatAsyncServiceTest {

    private ChatService chatService;
    private AsyncTaskService asyncTaskService;
    private ChatAsyncService service;

    private ChatConversation conversation;
    private ChatMessage userMessage;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        asyncTaskService = mock(AsyncTaskService.class);
        service = new ChatAsyncService(chatService, asyncTaskService);

        conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setKbId(3L);
        userMessage = new ChatMessage();
        userMessage.setId(100L);

        when(chatService.findOwned(7L, 10L)).thenReturn(conversation);
        when(chatService.saveUserMessage(any(), eq(7L), anyString(), anyString(), anyString()))
                .thenReturn(userMessage);
    }

    private AiRequestContext context() {
        return AiRequestContext.forBackground(7L, 10L, 3L, false, Set.of(),
                "req-1", "stream-new", "client-1");
    }

    @Test
    @DisplayName("首次提交：建任务并返回新 streamId")
    void shouldCreateTaskOnFirstSubmit() {
        when(asyncTaskService.findChatTask(100L)).thenReturn(null);
        AsyncTask task = new AsyncTask();
        task.setId(900L);
        when(asyncTaskService.createAiChatTask(any())).thenReturn(task);

        ChatAsyncService.ChatSubmission submission = service.submit(context(), "问题");

        assertEquals(900L, submission.taskId());
        assertEquals("stream-new", submission.streamId());
        assertEquals(10L, submission.conversationId());
        assertEquals(100L, submission.userMessageId());
        assertFalse(submission.reused());
        verify(asyncTaskService).createAiChatTask(any(ChatAnswerTaskDTO.class));
    }

    @Test
    @DisplayName("重复 clientMessageId：复用原任务与原 streamId，不再新建任务")
    void shouldReuseExistingTaskOnDuplicateClientMessageId() {
        AsyncTask existing = new AsyncTask();
        existing.setId(901L);
        existing.setPayloadJson(Map.of("streamId", "stream-old", "userMessageId", 100L));
        when(asyncTaskService.findChatTask(100L)).thenReturn(existing);

        ChatAsyncService.ChatSubmission submission = service.submit(context(), "问题");

        assertEquals(901L, submission.taskId());
        assertEquals("stream-old", submission.streamId());
        assertTrue(submission.reused());
        verify(asyncTaskService, never()).createAiChatTask(any());
    }

    @Test
    @DisplayName("空问题在写库前就被拒绝")
    void shouldRejectBlankQuestion() {
        assertThrows(RuntimeException.class, () -> service.submit(context(), " "));
        verify(chatService, never()).saveUserMessage(any(), anyLong(), anyString(), anyString(), anyString());
    }
}
