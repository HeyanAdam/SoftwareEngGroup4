package com.group4.kaoyan.common.mybatis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTypeHandlerTest {

    @Test
    void shouldRoundTripObject() {
        String json = JsonTypeHandler.toJson(Map.of("weak", List.of("英语", "数学")));
        Object parsed = JsonTypeHandler.fromJson(json);
        assertTrue(parsed instanceof Map);
        assertEquals(List.of("英语", "数学"), ((Map<?, ?>) parsed).get("weak"));
    }

    @Test
    void shouldKeepJsonTextAsIs() {
        String raw = "{\"a\":1}";
        assertEquals(raw, JsonTypeHandler.toJson(raw));
    }

    @Test
    void shouldReturnNullForBlank() {
        assertNull(JsonTypeHandler.toJson(null));
        assertNull(JsonTypeHandler.fromJson(null));
        assertNull(JsonTypeHandler.fromJson("  "));
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonTypeHandler.fromJson("{bad"));
    }

    @Test
    void shouldSerializeStringList() {
        assertEquals("[\"a\",\"b\"]", JsonTypeHandler.toJson(List.of("a", "b")));
    }
}
