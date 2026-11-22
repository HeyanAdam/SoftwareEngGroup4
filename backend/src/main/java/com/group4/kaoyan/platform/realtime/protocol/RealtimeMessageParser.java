package com.group4.kaoyan.platform.realtime.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 统一消息解析器（B-1 第 7 步）：所有客户端文本消息都从这里进来，坏消息在门口就被挡住。
 *
 * <p>校验顺序（哪一条不过就抛对应的 {@link RealtimeErrorCode}）：</p>
 * <ol>
 *   <li>非空；</li>
 *   <li>字节数 ≤ 32 KB（容器层还会再兜一层 {@code setTextMessageSizeLimit}）；</li>
 *   <li>合法 JSON 对象；</li>
 *   <li>{@code version == 1}；</li>
 *   <li>{@code type} 非空且不超长；</li>
 *   <li>{@code requestId} 非空且不超长；</li>
 *   <li>{@code chat.send} 的必填字段：{@code data.content} 非空、不超长，会话/知识库 ID 必须是整数。</li>
 * </ol>
 *
 * <p>注意：这里不认识业务语义，也不查库——只保证"送到 handler 的信封是干净的"，
 * 鉴权和越权校验在握手和 B-2 的业务处理器里做。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeMessageParser {

    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;

    /**
     * 解析并校验一条客户端消息。
     *
     * @param raw 原始文本帧
     * @return 干净的信封
     * @throws RealtimeProtocolException 任一校验不通过
     */
    public RealtimeEnvelope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "消息不能为空");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > properties.getMaxMessageBytes()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.MESSAGE_TOO_LARGE,
                    "单条消息不能超过 " + properties.getMaxMessageBytes() + " 字节");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "消息不是合法 JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "消息必须是 JSON 对象");
        }

        int version = readVersion(root);
        String type = readRequiredText(root, "type");
        String requestId = readRequiredText(root, "requestId");
        String clientMessageId = readOptionalText(root, "clientMessageId");
        JsonNode data = root.get("data");

        if (RealtimeProtocol.TYPE_CHAT_SEND.equals(type)) {
            validateChatSend(data, clientMessageId);
        } else if (RealtimeProtocol.TYPE_CHAT_CANCEL.equals(type)) {
            validateChatCancel(data);
        }

        return new RealtimeEnvelope(version, type, requestId, clientMessageId, data);
    }

    private int readVersion(JsonNode root) {
        JsonNode node = root.get("version");
        if (node == null || node.isNull()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "缺少 version 字段");
        }
        if (!node.isIntegralNumber()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "version 必须是整数");
        }
        int version = node.asInt();
        if (version != RealtimeProtocol.VERSION) {
            throw new RealtimeProtocolException(RealtimeErrorCode.UNSUPPORTED_VERSION,
                    "只支持 version=" + RealtimeProtocol.VERSION + "，收到 " + version);
        }
        return version;
    }

    private String readRequiredText(JsonNode root, String field) {
        String value = readOptionalText(root, field);
        if (value == null || value.isBlank()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "缺少 " + field + " 字段");
        }
        return value;
    }

    private String readOptionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, field + " 必须是字符串");
        }
        String value = node.asText();
        if (value.length() > properties.getMaxFieldLength()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE,
                    field + " 不能超过 " + properties.getMaxFieldLength() + " 个字符");
        }
        return value;
    }

    /**
     * chat.send 的必填字段校验。
     * 会话/知识库 ID 允许为空（新会话就是 null），但一旦给了就必须是整数。
     */
    private void validateChatSend(JsonNode data, String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE,
                    "chat.send 缺少 clientMessageId 字段");
        }
        if (data == null || !data.isObject()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "chat.send 缺少 data 对象");
        }
        JsonNode content = data.get("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, "chat.send 的 content 不能为空");
        }
        if (content.asText().length() > properties.getMaxTextLength()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE,
                    "content 不能超过 " + properties.getMaxTextLength() + " 个字符");
        }
        assertOptionalInteger(data, "conversationId");
        assertOptionalInteger(data, "kbId");
    }

    private void assertOptionalInteger(JsonNode data, String field) {
        JsonNode node = data.get(field);
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isIntegralNumber()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE, field + " 必须是整数或 null");
        }
    }

    private void validateChatCancel(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE,
                    "chat.cancel 缺少 data 对象");
        }
        JsonNode streamId = data.get("streamId");
        if (streamId == null || !streamId.isTextual() || streamId.asText().isBlank()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE,
                    "chat.cancel 的 streamId 不能为空");
        }
        if (streamId.asText().length() > properties.getMaxFieldLength()) {
            throw new RealtimeProtocolException(RealtimeErrorCode.BAD_MESSAGE,
                    "streamId 不能超过 " + properties.getMaxFieldLength() + " 个字符");
        }
    }
}
