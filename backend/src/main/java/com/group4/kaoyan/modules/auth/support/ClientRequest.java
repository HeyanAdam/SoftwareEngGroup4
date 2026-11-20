package com.group4.kaoyan.modules.auth.support;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * 认证请求的客户端信息（IP 与 User-Agent），用于登录日志、锁定和限流的维度。
 */
public record ClientRequest(String ip, String userAgent) {

    private static final int MAX_UA_LENGTH = 512;

    public static ClientRequest of(HttpServletRequest request) {
        if (request == null) {
            return new ClientRequest("unknown", null);
        }
        return new ClientRequest(resolveIp(request), truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH));
    }

    /** 经过 Nginx 时取 X-Forwarded-For 的第一段，否则取远端地址。 */
    static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    public String ipKey() {
        return ip == null ? "unknown" : ip.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
