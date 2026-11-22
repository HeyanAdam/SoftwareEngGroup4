package com.group4.kaoyan.modules.chat.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.chat.stream.ChatStreamEvent;
import com.group4.kaoyan.modules.chat.stream.ChatStreamService;
import com.group4.kaoyan.modules.chat.stream.ChatStreamStore;
import com.group4.kaoyan.platform.realtime.command.RealtimeCommandContext;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;
import com.group4.kaoyan.platform.realtime.limit.RealtimeChatRateLimiter;
import com.group4.kaoyan.platform.realtime.limit.RealtimeGenerationTracker;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeEnvelope;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRealtimeCommandHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatStreamService streamService;
    private ChatService chatService;
    private ChatStreamStore streamStore;
    private RbacSnapshotCache rbacCache;
    private AccountStatusGuard accountGuard;
    private RealtimeChatRateLimiter rateLimiter;
    private RealtimeDeliveryPort delivery;
    private ChatRealtimeCommandHandler handler;

    @BeforeEach
    void setUp() {
        streamService = mock(ChatStreamService.class);
        chatService = mock(ChatService.class);
        streamStore = mock(ChatStreamStore.class);
        rbacCache = mock(RbacSnapshotCache.class);
        accountGuard = mock(AccountStatusGuard.class);
        rateLimiter = mock(RealtimeChatRateLimiter.class);
        delivery = mock(RealtimeDeliveryPort.class);

        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxSendsPerWindow(6);
        properties.setMaxConcurrentGenerationsPerUser(2);
        properties.setMaxConcurrentGenerationsPerConnection(1);
        handler = new ChatRealtimeCommandHandler(
                streamService,
                chatService,
                streamStore,
                rbacCache,
                accountGuard,
                rateLimiter,
                new RealtimeGenerationTracker(),
                properties);

        when(rbacCache.get(7L)).thenReturn(new RbacSnapshotCache.Snapshot(
                List.of("STUDENT"), List.of("chat:send")));
        when(rateLimiter.allow(anyLong(), eq(6), any())).thenReturn(true);
    }

    @Test
    void shouldAckSenderThenBroadcastAllStreamEventsToAllUserConnections() throws Exception {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        when(chatService.getOrCreate(anyLong(), any(), any(), eq("这道题为什么选 B")))
                .thenReturn(conversation);

        ChatStreamEvent meta = ChatStreamEvent.of("meta", "req-1", "stream-1", 1,
                ChatStreamEvent.payload("conversationId", 10L));
        ChatStreamEvent delta = ChatStreamEvent.of("delta", "req-1", "stream-1", 2,
                ChatStreamEvent.payload("content", "选 B"));
        when(streamService.stream(any(), eq("这道题为什么选 B"), any()))
                .thenReturn(Flux.just(meta, delta));

        CountDownLatch ackLatch = new CountDownLatch(1);
        AtomicReference<RealtimeOutboundMessage> ack = new AtomicReference<>();
        RealtimeCommandContext context = new RealtimeCommandContext(
                7L, "tab-1", "chat", delivery, message -> {
                    ack.set(message);
                    ackLatch.countDown();
                });

        handler.handle(context, chatEnvelope("req-1", "client-1", "这道题为什么选 B"));

        assertTrue(ackLatch.await(2, TimeUnit.SECONDS));
        assertEquals(RealtimeProtocol.TYPE_ACK, ack.get().type());
        assertEquals(10L, ((Map<?, ?>) ack.get().data()).get("conversationId"));
        assertNotNull(((Map<?, ?>) ack.get().data()).get("streamId"));

        verify(streamService, timeout(2000)).stream(any(), eq("这道题为什么选 B"), any());
        verify(delivery, timeout(2000).times(2)).pushToUser(eq(7L),
                any(RealtimeOutboundMessage.class));
    }

    @Test
    void shouldRejectWhenRateLimitedWithoutCallingAiCore() throws Exception {
        when(rateLimiter.allow(anyLong(), eq(6), any())).thenReturn(false);
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<RealtimeOutboundMessage> error = new AtomicReference<>();
        RealtimeCommandContext context = new RealtimeCommandContext(
                7L, "tab-1", "chat", delivery, message -> {
                    error.set(message);
                    errorLatch.countDown();
                });

        handler.handle(context, chatEnvelope("req-2", "client-2", "问题"));

        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        assertEquals(RealtimeProtocol.TYPE_ERROR, error.get().type());
        assertEquals(4429, ((Map<?, ?>) error.get().data()).get("code"));
        verify(streamService, never()).stream(any(), any(), any());
        verify(delivery, never()).pushToUser(anyLong(), any());
    }

    @Test
    void shouldRejectDisabledAccount() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.ACCOUNT_DISABLED, "账号已禁用"))
                .when(accountGuard).ensureActive(7L);
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<RealtimeOutboundMessage> error = new AtomicReference<>();
        RealtimeCommandContext context = new RealtimeCommandContext(
                7L, "tab-1", "chat", delivery, message -> {
                    error.set(message);
                    errorLatch.countDown();
                });

        handler.handle(context, chatEnvelope("req-3", "client-3", "问题"));

        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        assertEquals(4403, ((Map<?, ?>) error.get().data()).get("code"));
        verify(chatService, never()).getOrCreate(anyLong(), any(), any(), any());
    }

    @Test
    void shouldRejectConversationOwnedByAnotherUser() throws Exception {
        when(chatService.getOrCreate(anyLong(), any(), any(), eq("问题")))
                .thenThrow(new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该数据"));
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<RealtimeOutboundMessage> error = new AtomicReference<>();
        RealtimeCommandContext context = new RealtimeCommandContext(
                7L, "tab-1", "chat", delivery, message -> {
                    error.set(message);
                    errorLatch.countDown();
                });

        handler.handle(context, chatEnvelope("req-4", "client-4", "问题"));

        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        assertEquals(4403, ((Map<?, ?>) error.get().data()).get("code"));
        verify(streamService, never()).stream(any(), any(), any());
    }

    @Test
    void shouldCancelActiveStream() throws Exception {
        when(streamService.cancel(7L, "stream-1")).thenReturn(true);

        handler.handle(new RealtimeCommandContext(7L, "tab-1", "chat", delivery, message -> {
        }), cancelEnvelope("cancel-1", "stream-1"));

        verify(streamService, timeout(2000)).cancel(7L, "stream-1");
        verify(delivery, never()).pushToUser(anyLong(), any());
    }

    @Test
    void repeatedCancelForFinishedStreamShouldReturnDoneIdempotently() throws Exception {
        when(streamService.cancel(7L, "stream-finished")).thenReturn(false);
        com.group4.kaoyan.modules.chat.entity.ChatMessage assistant =
                new com.group4.kaoyan.modules.chat.entity.ChatMessage();
        assistant.setId(99L);
        assistant.setUserId(7L);
        assistant.setContent("已保留的半段回答");
        assistant.setStatus(com.group4.kaoyan.modules.chat.entity.ChatMessage.STATUS_CANCELLED);
        assistant.setLastSequence(8L);
        when(chatService.findAssistantByStreamId("stream-finished")).thenReturn(assistant);

        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicReference<RealtimeOutboundMessage> done = new AtomicReference<>();
        handler.handle(new RealtimeCommandContext(7L, "tab-1", "chat", delivery, message -> {
            done.set(message);
            doneLatch.countDown();
        }), cancelEnvelope("cancel-2", "stream-finished"));

        assertTrue(doneLatch.await(2, TimeUnit.SECONDS));
        assertEquals(RealtimeProtocol.TYPE_DONE, done.get().type());
        assertEquals("cancelled", ((Map<?, ?>) done.get().data()).get("finishReason"));
        assertEquals(99L, ((Map<?, ?>) done.get().data()).get("messageId"));
        assertEquals(8L, ((Map<?, ?>) done.get().data()).get("lastSequence"));
    }

    private RealtimeEnvelope chatEnvelope(String requestId, String clientMessageId, String content)
            throws Exception {
        JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(Map.of(
                "conversationId", 10L,
                "content", content)));
        return new RealtimeEnvelope(1, RealtimeProtocol.TYPE_CHAT_SEND,
                requestId, clientMessageId, data);
    }

    private RealtimeEnvelope cancelEnvelope(String requestId, String streamId) throws Exception {
        JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(Map.of(
                "streamId", streamId)));
        return new RealtimeEnvelope(1, RealtimeProtocol.TYPE_CHAT_CANCEL,
                requestId, null, data);
    }
}
