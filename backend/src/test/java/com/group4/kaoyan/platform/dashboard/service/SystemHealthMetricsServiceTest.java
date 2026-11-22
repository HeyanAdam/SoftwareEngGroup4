package com.group4.kaoyan.platform.dashboard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 步骤 E4 的单元测试：三项探活相互独立、单项 2 秒超时。
 */
@DisplayName("系统探活指标（E4）")
class SystemHealthMetricsServiceTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
    private final CachingConnectionFactory rabbitConnectionFactory = mock(CachingConnectionFactory.class);
    private final RedisConnection redisConnection = mock(RedisConnection.class);
    private final org.springframework.amqp.rabbit.connection.Connection amqpConnection =
            mock(org.springframework.amqp.rabbit.connection.Connection.class);

    private SystemHealthMetricsService service() {
        return new SystemHealthMetricsService(dataSource, redisConnectionFactory, rabbitConnectionFactory);
    }

    @Test
    @DisplayName("三项都通：全部 UP")
    void allUp() throws Exception {
        Connection mysql = mock(Connection.class);
        when(mysql.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(mysql);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(rabbitConnectionFactory.createConnection()).thenReturn(amqpConnection);
        when(amqpConnection.isOpen()).thenReturn(true);

        SystemHealthMetricsService.SystemHealth health = service().probe();

        assertEquals("UP", health.mysql());
        assertEquals("UP", health.redis());
        assertEquals("UP", health.rabbitmq());
    }

    @Test
    @DisplayName("MySQL 抛异常：mysql DOWN，另外两项仍返回 UP")
    void mysqlDownDoesNotAffectOthers() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("连接池耗尽"));
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(rabbitConnectionFactory.createConnection()).thenReturn(amqpConnection);
        when(amqpConnection.isOpen()).thenReturn(true);

        SystemHealthMetricsService.SystemHealth health = service().probe();

        assertEquals("DOWN", health.mysql());
        assertEquals("UP", health.redis());
        assertEquals("UP", health.rabbitmq());
    }

    @Test
    @DisplayName("Redis 卡住超过 2 秒：判定 DOWN 且整体在 3 秒内返回")
    void slowProbeTimesOut() throws Exception {
        Connection mysql = mock(Connection.class);
        when(mysql.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(mysql);

        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenAnswer(invocation -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "PONG";
        });
        when(rabbitConnectionFactory.createConnection()).thenReturn(amqpConnection);
        when(amqpConnection.isOpen()).thenReturn(true);

        Instant start = Instant.now();
        SystemHealthMetricsService.SystemHealth health = service().probe();
        long elapsedMillis = Duration.between(start, Instant.now()).toMillis();

        assertEquals("DOWN", health.redis());
        assertEquals("UP", health.mysql());
        assertTrue(elapsedMillis < 3000,
                "超时控制应让接口在 2 秒量级返回，实际耗时 " + elapsedMillis + "ms");
    }
}
