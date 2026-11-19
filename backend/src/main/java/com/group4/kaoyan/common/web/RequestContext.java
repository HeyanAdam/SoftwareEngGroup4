package com.group4.kaoyan.common.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求上下文小工具：IP、UA、请求 ID。日志与审计统一从这里取，避免各处重复解析。
 */
public final class RequestContext {

    public static final String REQUEST_ID_ATTR = "kaoyan.requestId";

    private RequestContext() {
    }

    public static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /** X-Forwarded-For 取第一个非 unknown，其次 X-Real-IP，最后 remoteAddr。 */
    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            for (String part : forwarded.split(",")) {
                String ip = part.trim();
                if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return truncate(ip, 64);
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return truncate(realIp, 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    public static String userAgent(int maxLength) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), maxLength);
    }

    public static String requestId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object id = request.getAttribute(REQUEST_ID_ATTR);
        return id == null ? null : String.valueOf(id);
    }

    /** 所有字符串写库前统一截断，避免超长导致 DataIntegrityViolation。 */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
