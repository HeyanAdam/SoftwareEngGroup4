package com.group4.kaoyan.platform.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 * <p>
 * - /api/v1/health/live  存活探针：只要进程还在就返回 UP，不依赖外部中间件
 * - /api/v1/health/ready 就绪探针：逐个探活 MySQL、Redis、RabbitMQ
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final CachingConnectionFactory rabbitConnectionFactory;

    public HealthController(DataSource dataSource,
                            RedisConnectionFactory redisConnectionFactory,
                            CachingConnectionFactory rabbitConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
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
        String mysql = checkMysql();
        String redis = checkRedis();
        String rabbitmq = checkRabbitMq();

        Map<String, Object> body = new LinkedHashMap<>();
        boolean allUp = UP.equals(mysql) && UP.equals(redis) && UP.equals(rabbitmq);
        body.put("status", allUp ? UP : DOWN);
        body.put("mysql", mysql);
        body.put("redis", redis);
        body.put("rabbitmq", rabbitmq);
        body.put("time", now());
        return body;
    }

    private String checkMysql() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? UP : DOWN;
        } catch (Exception e) {
            log.warn("MySQL 健康检查失败：{}", e.getMessage());
            return DOWN;
        }
    }

    private String checkRedis() {
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            String pong = conn.ping();
            return pong != null ? UP : DOWN;
        } catch (Exception e) {
            log.warn("Redis 健康检查失败：{}", e.getMessage());
            return DOWN;
        }
    }

    private String checkRabbitMq() {
        try (org.springframework.amqp.rabbit.connection.Connection conn =
                     rabbitConnectionFactory.createConnection()) {
            return conn.isOpen() ? UP : DOWN;
        } catch (Exception e) {
            log.warn("RabbitMQ 健康检查失败：{}", e.getMessage());
            return DOWN;
        }
    }

    private String now() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
