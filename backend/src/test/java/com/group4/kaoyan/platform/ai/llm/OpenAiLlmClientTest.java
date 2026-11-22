package com.group4.kaoyan.platform.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.config.AiProperties;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-1 验收：401/429/5xx/超时映射为明确错误码，且重试语义正确。
 */
class OpenAiLlmClientTest {

    private OpenAiLlmClient client() {
        AiProperties props = new AiProperties();
        props.getLlm().setBaseUrl("http://127.0.0.1:1/v1");
        props.getLlm().setApiKey("test");
        return new OpenAiLlmClient(props, new ObjectMapper());
    }

    @Test
    @DisplayName("401/403 不重试，429/5xx 可重试，400 不重试")
    void shouldMapHttpStatus() {
        LlmStreamException auth = OpenAiLlmClient.mapStatus(401, "{\"error\":\"bad key\"}");
        assertEquals(LlmStreamException.AUTH_FAILED, auth.getCode());
        assertFalse(auth.isRetryable());

        LlmStreamException limited = OpenAiLlmClient.mapStatus(429, "");
        assertEquals(LlmStreamException.RATE_LIMITED, limited.getCode());
        assertTrue(limited.isRetryable());

        LlmStreamException upstream = OpenAiLlmClient.mapStatus(503, "busy");
        assertEquals(LlmStreamException.UPSTREAM_ERROR, upstream.getCode());
        assertTrue(upstream.isRetryable());

        LlmStreamException bad = OpenAiLlmClient.mapStatus(400, "bad request");
        assertEquals(LlmStreamException.BAD_REQUEST, bad.getCode());
        assertFalse(bad.isRetryable());
    }

    @Test
    @DisplayName("读超时映射为可重试的 LLM_TIMEOUT")
    void shouldMapReadTimeout() {
        LlmStreamException ex = client().translate(ReadTimeoutException.INSTANCE);

        assertEquals(LlmStreamException.TIMEOUT, ex.getCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("已经是 LlmStreamException 的异常原样返回，不重复包装")
    void shouldKeepExistingLlmException() {
        LlmStreamException original = new LlmStreamException(
                LlmStreamException.UPSTREAM_STREAM_CLOSED, "断流", true);

        assertSame(original, client().translate(original));
    }
}
