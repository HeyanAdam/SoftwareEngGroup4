package com.group4.kaoyan.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 缓存 JSON 请求体，供幂等拦截器读取哈希与回放。
 *
 * <p>multipart 请求（文件上传）不缓存：它的 body 是一次性流，缓存会吃掉文件内容。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodyCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().contains("multipart/");
        if (multipart || !hasBody(request)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new CachedBodyHttpServletRequest(request), response);
    }

    private boolean hasBody(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
