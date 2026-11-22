package com.group4.kaoyan.modules.chat.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A-1 验收：旧 SSE 事件名与负载必须与改造前一致。
 */
class ChatSseAdapterTest {

    @Test
    @DisplayName("references 仍然下发裸数组，老前端不用改解析")
    void referencesShouldStayArray() {
        ChatStreamEvent event = ChatStreamEvent.of("references", "r", "s", 2,
                Map.of("items", List.of(Map.of("index", 1, "fileName", "考研***.pdf"))));

        ChatSseAdapter.LegacyEvent legacy = ChatSseAdapter.legacyOf(event);

        assertEquals("references", legacy.name());
        assertEquals(List.of(Map.of("index", 1, "fileName", "考研***.pdf")), legacy.data());
    }

    @Test
    @DisplayName("delta 仍然只有 content 字段")
    void deltaShouldKeepContentOnly() {
        ChatStreamEvent event = ChatStreamEvent.of("delta", "r", "s", 3, Map.of("content", "新增文本"));

        ChatSseAdapter.LegacyEvent legacy = ChatSseAdapter.legacyOf(event);

        assertEquals("delta", legacy.name());
        assertEquals(Map.of("content", "新增文本"), legacy.data());
    }

    @Test
    @DisplayName("done 保留 messageId，error 由适配器额外补 done，不参与普通映射")
    void doneShouldKeepMessageId() {
        ChatStreamEvent done = ChatStreamEvent.of("done", "r", "s", 5,
                Map.of("messageId", 200L, "completionTokens", 0));

        ChatSseAdapter.LegacyEvent legacy = ChatSseAdapter.legacyOf(done);

        assertEquals("done", legacy.name());
        assertEquals(200L, ((Map<?, ?>) legacy.data()).get("messageId"));
        assertNull(ChatSseAdapter.legacyOf(ChatStreamEvent.of("error", "r", "s", 4,
                Map.of("code", "LLM_TIMEOUT"))));
    }
}
