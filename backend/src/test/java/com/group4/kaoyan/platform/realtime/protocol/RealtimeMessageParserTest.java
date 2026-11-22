package com.group4.kaoyan.platform.realtime.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 消息解析器单测（B-1 验收清单第 5 条：JSON、版本、大小、必填字段都有校验）。
 */
class RealtimeMessageParserTest {

    private RealtimeProperties properties;
    private RealtimeMessageParser parser;

    @BeforeEach
    void setUp() {
        properties = new RealtimeProperties();
        parser = new RealtimeMessageParser(new ObjectMapper(), properties);
    }

    @Test
    @DisplayName("合法 ping：解析出 type 和 requestId")
    void validPingShouldParse() {
        RealtimeEnvelope envelope = parser.parse(
                "{\"version\":1,\"type\":\"ping\",\"requestId\":\"ping-1\"}");

        assertEquals(RealtimeProtocol.TYPE_PING, envelope.type());
        assertEquals("ping-1", envelope.requestId());
        assertEquals(1, envelope.version());
    }

    @Test
    @DisplayName("合法 chat.send：content 必填字段通过，会话 ID 可为 null")
    void validChatSendShouldParse() {
        RealtimeEnvelope envelope = parser.parse("""
                {"version":1,"type":"chat.send","requestId":"r-1","clientMessageId":"c-1",
                 "data":{"conversationId":null,"kbId":null,"content":"这道题为什么选 B？"}}
                """);

        assertEquals(RealtimeProtocol.TYPE_CHAT_SEND, envelope.type());
        assertEquals("这道题为什么选 B？", envelope.text("content"));
        assertNull(envelope.number("conversationId"));
        assertNotNull(envelope.data());
    }

    @Test
    @DisplayName("不是 JSON：4400")
    void invalidJsonShouldFail() {
        RealtimeProtocolException ex = assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("not-a-json"));
        assertEquals(RealtimeErrorCode.BAD_MESSAGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("版本不是 1：4426")
    void unsupportedVersionShouldFail() {
        RealtimeProtocolException ex = assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("{\"version\":2,\"type\":\"ping\",\"requestId\":\"p\"}"));
        assertEquals(RealtimeErrorCode.UNSUPPORTED_VERSION, ex.getErrorCode());
    }

    @Test
    @DisplayName("缺 version / 缺 type / 缺 requestId：4400")
    void missingRequiredFieldsShouldFail() {
        assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("{\"type\":\"ping\",\"requestId\":\"p\"}"));
        assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("{\"version\":1,\"requestId\":\"p\"}"));
        assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("{\"version\":1,\"type\":\"ping\"}"));
    }

    @Test
    @DisplayName("消息体超过上限：4413")
    void oversizeMessageShouldFail() {
        properties.setMaxMessageBytes(20);
        RealtimeProtocolException ex = assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("{\"version\":1,\"type\":\"ping\",\"requestId\":\"p\"}"));
        assertEquals(RealtimeErrorCode.MESSAGE_TOO_LARGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("chat.send 缺 content：4400")
    void chatSendWithoutContentShouldFail() {
        RealtimeProtocolException ex = assertThrows(RealtimeProtocolException.class,
                () -> parser.parse("{\"version\":1,\"type\":\"chat.send\",\"requestId\":\"r\",\"data\":{}}"));
        assertEquals(RealtimeErrorCode.BAD_MESSAGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("chat.send 正文超长：4400（上限可配置）")
    void chatSendTooLongShouldFail() {
        properties.setMaxTextLength(5);
        assertThrows(RealtimeProtocolException.class, () -> parser.parse("""
                {"version":1,"type":"chat.send","requestId":"r","data":{"content":"123456"}}
                """));
    }

    @Test
    @DisplayName("conversationId 不是整数：4400")
    void chatSendWithNonIntegerConversationIdShouldFail() {
        assertThrows(RealtimeProtocolException.class, () -> parser.parse("""
                {"version":1,"type":"chat.send","requestId":"r","data":{"conversationId":"abc","content":"hi"}}
                """));
    }
}
