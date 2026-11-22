package com.group4.kaoyan.modules.chat.stream;

/**
 * 流式链路可枚举的失败：错误码直接下发给客户端，{@code retryable} 决定是否自动重试。
 */
public class ChatStreamException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public ChatStreamException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public ChatStreamException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static ChatStreamException invalidRequest(String message) {
        return new ChatStreamException("INVALID_REQUEST", message, false);
    }

    public static ChatStreamException internal(String message, Throwable cause) {
        return new ChatStreamException("INTERNAL_ERROR", message == null ? "服务内部错误" : message,
                false, cause);
    }
}
