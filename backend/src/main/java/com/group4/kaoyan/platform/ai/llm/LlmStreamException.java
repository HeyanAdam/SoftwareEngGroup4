package com.group4.kaoyan.platform.ai.llm;

/**
 * 模型侧失败：上游状态码、超时、断流统一映射成这里的错误码，不再向上抛裸
 * {@code WebClientResponseException}（避免把供应商响应体泄给前端）。
 */
public class LlmStreamException extends RuntimeException {

    public static final String AUTH_FAILED = "LLM_AUTH_FAILED";
    public static final String RATE_LIMITED = "LLM_RATE_LIMITED";
    public static final String TIMEOUT = "LLM_TIMEOUT";
    public static final String UPSTREAM_ERROR = "LLM_UPSTREAM_ERROR";
    public static final String UPSTREAM_STREAM_CLOSED = "LLM_STREAM_CLOSED";
    public static final String BAD_REQUEST = "LLM_BAD_REQUEST";
    public static final String BAD_RESPONSE = "LLM_BAD_RESPONSE";
    public static final String NETWORK_ERROR = "LLM_NETWORK_ERROR";

    private final String code;
    private final boolean retryable;
    private final int upstreamStatus;

    public LlmStreamException(String code, String message, boolean retryable, int upstreamStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
        this.upstreamStatus = upstreamStatus;
    }

    public LlmStreamException(String code, String message, boolean retryable) {
        this(code, message, retryable, 0, null);
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
