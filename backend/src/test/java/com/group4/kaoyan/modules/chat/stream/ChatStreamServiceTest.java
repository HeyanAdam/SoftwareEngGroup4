package com.group4.kaoyan.modules.chat.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.knowledge.service.PromptTemplateLoader;
import com.group4.kaoyan.modules.knowledge.service.RetrievalService;
import com.group4.kaoyan.platform.ai.llm.LlmClient;
import com.group4.kaoyan.platform.ai.llm.LlmStreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-1 核心验收：事件顺序与序号连续、无检索结果仍能生成、错误映射、取消清理。
 *
 * <p>不启动 Spring 上下文，也不触碰 SSE —— 本类本来就是给 HTTP 适配器和 MQ 消费者共用的
 * 编排层，能用纯 Flux 单测覆盖。</p>
 */
class ChatStreamServiceTest {

    private ChatService chatService;
    private RetrievalService retrievalService;
    private PromptTemplateLoader promptLoader;
    private LlmClient llmClient;
    private ChatStreamService service;

    private ChatConversation conversation;
    private ChatMessage userMessage;
    private ChatMessage assistantMessage;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        retrievalService = mock(RetrievalService.class);
        promptLoader = mock(PromptTemplateLoader.class);
        llmClient = mock(LlmClient.class);

        AiProperties props = new AiProperties();
        props.getLlm().setModel("deepseek-chat");
        service = new ChatStreamService(chatService, retrievalService, promptLoader,
                llmClient, props, new ObjectMapper());

        conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setKbId(3L);

        userMessage = new ChatMessage();
        userMessage.setId(100L);
        assistantMessage = new ChatMessage();
        assistantMessage.setId(200L);

        when(chatService.findOwned(7L, 10L)).thenReturn(conversation);
        when(chatService.recentHistory(10L)).thenReturn("学生：上一题\n助教：选 B\n");
        when(chatService.saveUserMessage(any(), eq(7L), anyString(), any(), anyString())).thenReturn(userMessage);
        when(chatService.saveAssistantMessage(any(), eq(7L), anyString(), anyString(), anyString()))
                .thenReturn(assistantMessage);
        when(promptLoader.render(anyString(), anyString(), anyString())).thenReturn("系统提示");
        when(retrievalService.buildContext(any())).thenReturn("（未检索到相关片段）");
    }

    private AiRequestContext context(boolean admin) {
        return AiRequestContext.forBackground(7L, 10L, null, admin, Set.of(),
                "req-1", "stream-1", "client-1");
    }

    private List<ChatStreamEvent> collect(AiRequestContext ctx, String question) {
        return service.stream(ctx, question).collectList().block(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("正常生成：meta→references→delta…→done，sequence 从 1 连续递增")
    void shouldEmitOrderedEventsWithContinuousSequence() {
        when(retrievalService.retrieve(eq(3L), eq("什么是调剂"), eq(false))).thenReturn(List.of(
                new RetrievalService.Retrieved("2026考研调剂手册.pdf", 11L, 22L, "调剂需要满足国家线", 0.82f)));
        when(llmClient.streamChat(any())).thenReturn(Flux.just("调剂", "要过线"));

        List<ChatStreamEvent> events = collect(context(false), "什么是调剂");

        List<String> types = events.stream().map(ChatStreamEvent::type).collect(Collectors.toList());
        assertEquals(List.of("meta", "references", "delta", "delta", "done"), types);
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1L, events.get(i).sequence(), "sequence 必须从 1 连续");
            assertEquals(1, events.get(i).version());
            assertEquals("req-1", events.get(i).requestId());
            assertEquals("stream-1", events.get(i).streamId());
            assertNotNull(events.get(i).serverTime());
        }
        assertEquals(10L, events.get(0).data().get("conversationId"));
        assertEquals(200L, events.get(events.size() - 1).data().get("messageId"));
        verify(chatService).saveAssistantMessage(any(), eq(7L), eq("调剂要过线"), anyString(), eq("deepseek-chat"));
    }

    @Test
    @DisplayName("普通学生：引用只看脱敏文件名、摘要与相似度，不下发 docId/chunkId")
    @SuppressWarnings("unchecked")
    void shouldDesensitizeReferencesForStudent() {
        when(retrievalService.retrieve(anyLong(), anyString(), anyBoolean())).thenReturn(List.of(
                new RetrievalService.Retrieved("2026考研调剂手册.pdf", 11L, 22L, "调剂需要满足国家线", 0.82f)));
        when(llmClient.streamChat(any())).thenReturn(Flux.just("好"));

        List<ChatStreamEvent> events = collect(context(false), "什么是调剂");
        Map<String, Object> refsEvent = events.stream()
                .filter(e -> "references".equals(e.type())).findFirst().orElseThrow().data();
        List<Map<String, Object>> items = (List<Map<String, Object>>) refsEvent.get("items");

        assertEquals(1, items.size());
        assertEquals("20***.pdf", items.get(0).get("fileName"));
        assertEquals(0.82, (Double) items.get(0).get("score"), 0.001);
        assertFalse(items.get(0).containsKey("docId"));
        assertFalse(items.get(0).containsKey("chunkId"));
        assertFalse(items.get(0).containsKey("docName"));
    }

    @Test
    @DisplayName("管理员：引用保留文档/切片 ID，便于后台核对")
    @SuppressWarnings("unchecked")
    void shouldKeepFullReferencesForAdmin() {
        when(retrievalService.retrieve(anyLong(), anyString(), eq(true))).thenReturn(List.of(
                new RetrievalService.Retrieved("2026考研调剂手册.pdf", 11L, 22L, "调剂需要满足国家线", 0.82f)));
        when(llmClient.streamChat(any())).thenReturn(Flux.just("好"));

        List<ChatStreamEvent> events = collect(context(true), "什么是调剂");
        List<Map<String, Object>> items = (List<Map<String, Object>>) events.stream()
                .filter(e -> "references".equals(e.type())).findFirst().orElseThrow()
                .data().get("items");

        assertEquals(22L, items.get(0).get("docId"));
        assertEquals(11L, items.get(0).get("chunkId"));
        assertEquals("2026考研调剂手册.pdf", items.get(0).get("docName"));
    }

    @Test
    @DisplayName("检索为空：仍然带着占位上下文完成生成，不中断")
    void shouldStillGenerateWhenRetrievalEmpty() {
        when(retrievalService.retrieve(anyLong(), anyString(), anyBoolean())).thenReturn(List.of());
        when(llmClient.streamChat(any())).thenReturn(Flux.just("根据常识…"));

        List<ChatStreamEvent> events = collect(context(false), "没有资料的问题");

        assertEquals(List.of("meta", "references", "delta", "done"),
                events.stream().map(ChatStreamEvent::type).toList());
        verify(promptLoader).render(eq("（未检索到相关片段）"), anyString(), eq("没有资料的问题"));
    }

    @Test
    @DisplayName("模型失败：把上游错误映射成 error 事件，并把已生成片段落库")
    void shouldMapModelErrorToErrorEvent() {
        when(retrievalService.retrieve(anyLong(), anyString(), anyBoolean())).thenReturn(List.of());
        when(llmClient.streamChat(any())).thenReturn(Flux.concat(
                Flux.just("半句"),
                Flux.error(new LlmStreamException(LlmStreamException.RATE_LIMITED, "模型限流（HTTP 429）", true))));

        List<ChatStreamEvent> events = collect(context(false), "问题");

        ChatStreamEvent last = events.get(events.size() - 1);
        assertEquals("error", last.type());
        assertEquals("LLM_RATE_LIMITED", last.data().get("code"));
        assertEquals(true, last.data().get("retryable"));
        assertEquals("半句", ((ChatStreamEvent) events.get(events.size() - 2)).data().get("content"));
        verify(chatService).saveAssistantMessage(any(), eq(7L), eq("半句"), anyString(), anyString());
    }

    @Test
    @DisplayName("空问题：只发一个不可重试的 INVALID_REQUEST，不建会话不落库")
    void shouldRejectBlankQuestion() {
        List<ChatStreamEvent> events = collect(context(false), "   ");

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).type());
        assertEquals("INVALID_REQUEST", events.get(0).data().get("code"));
        assertEquals(false, events.get(0).data().get("retryable"));
        verify(chatService, never()).findOwned(anyLong(), anyLong());
        verify(chatService, never()).saveUserMessage(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("订阅取消：释放模型订阅，且不产生空回答记录")
    void shouldDisposeOnCancel() {
        when(retrievalService.retrieve(anyLong(), anyString(), anyBoolean())).thenReturn(List.of());
        when(llmClient.streamChat(any())).thenReturn(Flux.never());

        List<ChatStreamEvent> head = service.stream(context(false), "问题")
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertEquals(List.of("meta", "references"), head.stream().map(ChatStreamEvent::type).toList());
        verify(chatService, never()).saveAssistantMessage(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("文件名脱敏：保留前两个字与扩展名")
    void shouldMaskFileName() {
        assertEquals("考研***.pdf", ChatStreamService.maskFileName("考研数学真题.pdf"));
        assertEquals("资***", ChatStreamService.maskFileName("资料"));
        assertEquals("A***.docx", ChatStreamService.maskFileName("A.docx"));
        assertEquals("资料", ChatStreamService.maskFileName(null));
        assertTrue(ChatStreamService.maskFileName("考研数学真题.pdf").contains("***"));
    }
}

