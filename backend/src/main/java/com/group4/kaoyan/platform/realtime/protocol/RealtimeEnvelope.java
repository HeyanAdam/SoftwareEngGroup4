package com.group4.kaoyan.platform.realtime.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 解出来的客户端消息信封（协议第 3 节）。
 *
 * <pre>
 * { "version": 1, "type": "chat.send", "requestId": "...", "clientMessageId": "...", "data": { ... } }
 * </pre>
 *
 * @param version         协议版本，目前只认 1
 * @param type            消息类型，见 {@link RealtimeProtocol}
 * @param requestId       客户端生成的请求 ID，服务端每个回包都原样带回，用来对账
 * @param clientMessageId 客户端消息 ID，仅 chat.send 使用，用于幂等
 * @param data            业务数据，不做强类型绑定（B-2 才需要具体字段）
 */
public record RealtimeEnvelope(
        int version,
        String type,
        String requestId,
        String clientMessageId,
        JsonNode data
) {

    /** data 里的文本字段，取不到返回 null。 */
    public String text(String field) {
        if (data == null || !data.isObject()) {
            return null;
        }
        JsonNode node = data.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** data 里的数字字段（Long），取不到或类型不对返回 null；不在这里报错，交给业务判空。 */
    public Long number(String field) {
        if (data == null || !data.isObject()) {
            return null;
        }
        JsonNode node = data.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isIntegralNumber() ? node.asLong() : null;
    }
}
