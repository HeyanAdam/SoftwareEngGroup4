package com.group4.kaoyan.platform.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-1 验收：标准 SSE data: 分块解析、结束标记、跨行 JSON、上游 error 负载。
 */
class OpenAiSseParserTest {

    private OpenAiSseParser parser() {
        return new OpenAiSseParser(new ObjectMapper());
    }

    @Test
    @DisplayName("单行分块 + [DONE]：逐块抽取 content，完成标记置位")
    void shouldParseSingleLineChunks() {
        OpenAiSseParser parser = parser();

        assertEquals(List.of("你"), parser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n"));
        assertEquals(List.of("好"), parser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n"));
        assertEquals(List.of(), parser.parse("data: [DONE]\n"));

        assertTrue(parser.isCompleted());
        assertEquals(2, parser.deltaCount());
    }

    @Test
    @DisplayName("一次收到多行事件、注释行与空行都能正确跳过")
    void shouldParseMultipleLinesAndIgnoreComments() {
        OpenAiSseParser parser = parser();

        List<String> deltas = parser.parse("""
                : keep-alive
                event: message
                data: {"choices":[{"delta":{"content":"第一段"}}]}

                data: {"choices":[{"delta":{"content":"第二段"}}]}
                """);

        assertEquals(List.of("第一段", "第二段"), deltas);
        assertFalse(parser.isCompleted());
    }

    @Test
    @DisplayName("JSON 被拆到两条 data 行：按标准 SSE 拼接后仍能解析")
    void shouldJoinMultiLineDataPayload() {
        OpenAiSseParser parser = parser();

        assertEquals(List.of(), parser.parse("data: {\"choices\":[{\"delta\":{\"content\":\n"));
        assertEquals(List.of("拼起来"), parser.parse("data: \"拼起来\"}}]}\n"));
    }

    @Test
    @DisplayName("上游在 data 里回 error：抛可重试的 LlmStreamException")
    void shouldMapInlineErrorPayload() {
        OpenAiSseParser parser = parser();

        LlmStreamException ex = assertThrows(LlmStreamException.class, () -> parser.parse(
                "data: {\"error\":{\"message\":\"quota exceeded\"}}\n"));

        assertEquals(LlmStreamException.UPSTREAM_ERROR, ex.getCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("流结束仍有残留半截 JSON：finish() 强制刷新，不抛异常")
    void finishShouldFlushPendingNoise() {
        OpenAiSseParser parser = parser();

        parser.parse("data: {\"choices\":[{\"delta\":{\"content\":\"半\"");
        List<String> rest = parser.finish();

        assertEquals(List.of(), rest);
    }
}
