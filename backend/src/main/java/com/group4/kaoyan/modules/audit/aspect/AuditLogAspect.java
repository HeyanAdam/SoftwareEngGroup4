package com.group4.kaoyan.modules.audit.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.mybatis.JsonTypeHandler;
import com.group4.kaoyan.common.web.RequestContext;
import com.group4.kaoyan.modules.audit.annotation.AuditLog;
import com.group4.kaoyan.modules.audit.entity.AuditLogRecord;
import com.group4.kaoyan.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计切面：成功返回后异步落库，异常不影响业务。
 *
 * <p>请求相关字段（IP、requestId、操作人）必须在主线程取好再交给异步线程，
 * 异步线程里没有 RequestContextHolder。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    /** after_json 最大 8KB，超出后只存摘要，避免 JSON 列写入失败 */
    private static final int AFTER_JSON_MAX = 8192;
    private static final int RESOURCE_ID_MAX = 64;

    private final AuditLogService auditLogService;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            AuditLogRecord record = new AuditLogRecord();
            record.setUserId(currentUserId());
            record.setModule(RequestContext.truncate(auditLog.module(), 64));
            record.setAction(RequestContext.truncate(auditLog.action(), 64));
            record.setResourceType(RequestContext.truncate(auditLog.resourceType(), 64));
            record.setResourceId(RequestContext.truncate(resolveResourceId(joinPoint), RESOURCE_ID_MAX));
            record.setRequestId(RequestContext.truncate(RequestContext.requestId(), 64));
            record.setIpAddress(RequestContext.clientIp());
            record.setAfterJson(summarize(result));
            record.setCreateTime(LocalDateTime.now());
            auditLogService.save(record);
        } catch (Exception e) {
            // 审计是旁路，绝不能因为序列化/取参失败影响业务返回
            log.warn("审计摘要采集失败，已忽略：{}", e.getMessage());
        }
        return result;
    }

    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    /** 资源 ID：优先取方法参数里的第一个数字型参数，其次取 URI 末尾的数字段。 */
    private String resolveResourceId(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long || arg instanceof Integer) {
                return String.valueOf(arg);
            }
        }
        String uri = RequestContext.currentRequest() == null
                ? null : RequestContext.currentRequest().getRequestURI();
        if (uri == null) {
            return null;
        }
        String last = uri.substring(uri.lastIndexOf('/') + 1);
        return last.chars().allMatch(Character::isDigit) ? last : null;
    }

    /** 返回体摘要：超过 8KB 时只保留预览，保证写入的是合法 JSON。 */
    private Object summarize(Object result) {
        if (result == null) {
            return null;
        }
        String json;
        try {
            json = JsonTypeHandler.toJson(result);
        } catch (Exception e) {
            return Map.of("unserializable", e.getClass().getSimpleName());
        }
        if (json == null) {
            return null;
        }
        if (json.length() <= AFTER_JSON_MAX) {
            return json;
        }
        return Map.of(
                "truncated", true,
                "length", json.length(),
                "preview", json.substring(0, 2000)
        );
    }
}
