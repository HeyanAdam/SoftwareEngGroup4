package com.group4.kaoyan.platform.idempotency;

import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.mybatis.JsonTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 采集标注了 @Idempotent 的接口的响应体，供 afterCompletion 落成 SUCCESS 记录。
 * 只在拦截器写入了 recordId 的请求上工作，其他请求零影响。
 */
@Slf4j
@ControllerAdvice
public class IdempotencyResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest raw = servletRequest.getServletRequest();
        if (raw.getAttribute(IdempotencyInterceptor.ATTR_RECORD_ID) == null || body == null) {
            return body;
        }
        try {
            Integer code = body instanceof R<?> r ? r.getCode() : null;
            raw.setAttribute(IdempotencyInterceptor.ATTR_RESPONSE,
                    new IdempotencyInterceptor.CapturedResponse(code, JsonTypeHandler.toJson(body)));
        } catch (Exception e) {
            log.warn("幂等响应采集失败：{}", e.getMessage());
        }
        return body;
    }
}
