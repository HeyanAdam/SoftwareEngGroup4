package com.group4.kaoyan.platform.health;

import com.group4.kaoyan.platform.dashboard.service.SystemHealthMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 * <p>
 * - /api/v1/health/live  存活探针：只要进程还在就返回 UP，不依赖外部中间件
 * - /api/v1/health/ready 就绪探针：逐个探活 MySQL、Redis、RabbitMQ
 *
 * <p>探活逻辑已抽到 {@link SystemHealthMetricsService}：大屏聚合接口与就绪探针
 * 共用同一套「单项 2 秒超时、相互独立」的实现，Controller 只负责拼响应结构，
 * 不再重复维护三份连接检查。</p>
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final SystemHealthMetricsService systemHealthMetricsService;

    public HealthController(SystemHealthMetricsService systemHealthMetricsService) {
        this.systemHealthMetricsService = systemHealthMetricsService;
    }

    /** 存活探针：不检查下游依赖，避免中间件抖动导致容器被反复重启 */
    @GetMapping("/live")
    public Map<String, Object> live() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", UP);
        body.put("service", "kaoyan-backend");
        body.put("time", now());
        return body;
    }

    /** 就绪探针：MySQL / Redis / RabbitMQ 任一不通则整体 DOWN */
    @GetMapping("/ready")
    public Map<String, Object> ready() {
        SystemHealthMetricsService.SystemHealth health = systemHealthMetricsService.probe();

        Map<String, Object> body = new LinkedHashMap<>();
        boolean allUp = UP.equals(health.mysql()) && UP.equals(health.redis()) && UP.equals(health.rabbitmq());
        body.put("status", allUp ? UP : DOWN);
        body.put("mysql", health.mysql());
        body.put("redis", health.redis());
        body.put("rabbitmq", health.rabbitmq());
        body.put("time", now());
        return body;
    }

    private String now() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
