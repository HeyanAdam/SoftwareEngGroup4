package com.group4.kaoyan.platform.realtime.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeMessageWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RealtimeMessageWriter writer = new RealtimeMessageWriter(objectMapper);

    @Test
    void errorUsesStableCodeReasonRetryableShape() throws Exception {
        JsonNode root = objectMapper.readTree(
                writer.error(RealtimeErrorCode.BAD_MESSAGE, "request-1"));

        assertEquals(1, root.path("version").asInt());
        assertEquals(RealtimeProtocol.TYPE_ERROR, root.path("type").asText());
        assertEquals("request-1", root.path("requestId").asText());

        JsonNode data = root.path("data");
        assertEquals(3, data.size());
        assertEquals(4400, data.path("code").asInt());
        assertEquals("消息格式不合法", data.path("reason").asText());
        assertFalse(data.path("retryable").asBoolean());
    }

    @Test
    void retryableIsDeclaredByErrorCode() {
        Set<RealtimeErrorCode> expected = Set.of(
                RealtimeErrorCode.IDLE_TIMEOUT,
                RealtimeErrorCode.TOO_MANY_CONNECTIONS,
                RealtimeErrorCode.RATE_LIMITED,
                RealtimeErrorCode.INTERNAL_ERROR);

        Set<RealtimeErrorCode> actual = Arrays.stream(RealtimeErrorCode.values())
                .filter(RealtimeErrorCode::isRetryable)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
        assertFalse(RealtimeErrorCode.UNAUTHORIZED.isRetryable());
        assertFalse(RealtimeErrorCode.FORBIDDEN.isRetryable());
    }

    @Test
    void customReasonKeepsRetryableFlag() throws Exception {
        JsonNode data = objectMapper.readTree(
                        writer.error(RealtimeErrorCode.INTERNAL_ERROR, "request-2", "回答流中断"))
                .path("data");

        assertEquals(4500, data.path("code").asInt());
        assertEquals("回答流中断", data.path("reason").asText());
        assertTrue(data.path("retryable").asBoolean());
    }
}
