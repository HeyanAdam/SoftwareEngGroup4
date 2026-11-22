package com.group4.kaoyan.platform.realtime.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端回包的统一序列化入口：所有出站消息都在这里拼 {@code version/type/requestId/data}。
 *
 * <p>为什么不用现成的 record 直接序列化：字段顺序对前端调试、抓包比对都更友好，
 * 而且错误包的 {@code code + reason + retryable} 结构只有一个地方能改。</p>
 */
@Component
@RequiredArgsConstructor
public class RealtimeMessageWriter {

    private final ObjectMapper objectMapper;

    /** 任意业务事件（B-2 的 ack/meta/delta/done 也走这里）。 */
    public String toJson(String type, String requestId, Object data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", RealtimeProtocol.VERSION);
        root.put("type", type);
        if (requestId != null) {
            root.put("requestId", requestId);
        }
        root.put("data", data == null ? Map.of() : data);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("WebSocket 回包序列化失败", e);
        }
    }

    /** 心跳应答。 */
    public String pong(String requestId) {
        return toJson(RealtimeProtocol.TYPE_PONG, requestId, Map.of());
    }

    /** 错误包：{@code data = {code, reason, retryable}}。 */
    public String error(RealtimeErrorCode code, String requestId) {
        return error(code, requestId, code.getReason());
    }

    /** 错误包，可覆盖原因文案（例如带上"content 不能超过 4000 个字符"这类具体提示）。 */
    public String error(RealtimeErrorCode code, String requestId, String detail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code.getCode());
        data.put("reason", detail == null || detail.isBlank() ? code.getReason() : detail);
        data.put("retryable", code.isRetryable());
        return toJson(RealtimeProtocol.TYPE_ERROR, requestId, data);
    }
}
