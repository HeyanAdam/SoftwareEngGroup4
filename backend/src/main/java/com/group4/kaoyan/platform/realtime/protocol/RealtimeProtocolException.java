package com.group4.kaoyan.platform.realtime.protocol;

import lombok.Getter;

/**
 * 协议校验失败。带着 {@link RealtimeErrorCode} 抛出来，
 * handler 统一把它翻译成 {@code type=error} 的回包或关闭帧，业务层不需要自己拼错误码。
 */
@Getter
public class RealtimeProtocolException extends RuntimeException {

    private final RealtimeErrorCode errorCode;

    public RealtimeProtocolException(RealtimeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RealtimeProtocolException(RealtimeErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
