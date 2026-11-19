package com.group4.kaoyan.platform.idempotency;

import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.web.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 幂等拦截器：只对标注 @Idempotent 的方法生效。
 *
 * <p>生命周期：preHandle 抢占 PROCESSING → 正常返回后由 {@link IdempotencyResponseAdvice}
 * 记下响应体 → afterCompletion 写 SUCCESS/FAILED。成功后重复请求直接回放上次响应。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";
    public static final String ATTR_RECORD_ID = "kaoyan.idempotency.recordId";
    public static final String ATTR_RESPONSE = "kaoyan.idempotency.response";

    private final IdempotencyService idempotencyService;

    /** 由 ResponseBodyAdvice 采集的响应快照。 */
    public record CapturedResponse(Integer code, String body) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || handlerMethod.getMethodAnnotation(Idempotent.class) == null) {
            return true;
        }
        String key = request.getHeader(HEADER);
        Long userId = StpUtil.getLoginIdAsLong();
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        String requestHash = hashBody(request);

        IdempotencyService.Begin begin = idempotencyService.begin(userId, endpoint, key, requestHash);
        if (begin.replay()) {
            writeReplay(response, begin.replayBody());
            return false;
        }
        request.setAttribute(ATTR_RECORD_ID, begin.recordId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object value = request.getAttribute(ATTR_RECORD_ID);
        if (!(value instanceof Long recordId)) {
            return;
        }
        if (ex != null) {
            idempotencyService.fail(recordId);
            return;
        }
        Object captured = request.getAttribute(ATTR_RESPONSE);
        if (captured instanceof CapturedResponse snapshot) {
            idempotencyService.success(recordId, snapshot.code(), snapshot.body());
        } else {
            // 响应没采集到（比如异步流式输出），保守记为失败，允许调用方重试
            idempotencyService.fail(recordId);
        }
    }

    /** 请求体 SHA-256；multipart 或没有缓存包装时返回 null（不参与哈希比对）。 */
    private String hashBody(HttpServletRequest request) {
        if (!(request instanceof CachedBodyHttpServletRequest cached)) {
            return null;
        }
        byte[] body = cached.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void writeReplay(HttpServletResponse response, String body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body == null
                ? "{\"code\":0,\"message\":\"ok\",\"data\":null}" : body);
        response.flushBuffer();
    }
}
