package com.group4.kaoyan.platform.dashboard.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 中间件探活指标（步骤 E4）。
 *
 * <p>把 HealthController 里原本内联的三段探活抽成服务，供两个调用方复用：
 * 就绪探针 {@code /api/v1/health/ready} 与大屏聚合接口。Controller 不再自己拼探活逻辑。</p>
 *
 * <p>三条约定：</p>
 * <ol>
 *   <li><b>单项 2 秒上限</b>：每项在独立线程里执行，{@code get(2s)} 超时即判 DOWN 并取消任务，
 *       避免某个中间件 TCP 半开时把整页拖到 30 秒。</li>
 *   <li><b>相互独立</b>：三项并行执行，一项 DOWN 不影响其他两项的返回值。</li>
 *   <li><b>日志只写异常摘要</b>：只记录消息文本，不打印连接串、账号或密码。</li>
 * </ol>
 */
@Slf4j
@Service
public class SystemHealthMetricsService {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    /** 单项探活超时：2 秒。 */
    public static final long PROBE_TIMEOUT_SECONDS = 2L;

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final CachingConnectionFactory rabbitConnectionFactory;

    /** 探活专用线程池：守护线程，避免容器停机时阻塞 JVM 退出。 */
    private final ExecutorService probeExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "dashboard-probe");
        thread.setDaemon(true);
        return thread;
    });

    public SystemHealthMetricsService(DataSource dataSource,
                                      RedisConnectionFactory redisConnectionFactory,
                                      CachingConnectionFactory rabbitConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
    }

    /** 探活结果：三个中间件各自的状态，只允许 UP / DOWN。 */
    public record SystemHealth(String mysql, String redis, String rabbitmq) {
    }

    /**
     * 并行探活 MySQL / Redis / RabbitMQ。
     *
     * @return 三项状态，互不阻塞
     */
    public SystemHealth probe() {
        Future<Boolean> mysql = probeExecutor.submit(this::checkMysql);
        Future<Boolean> redis = probeExecutor.submit(this::checkRedis);
        Future<Boolean> rabbitmq = probeExecutor.submit(this::checkRabbitMq);
        return new SystemHealth(
                statusOf("MySQL", mysql),
                statusOf("Redis", redis),
                statusOf("RabbitMQ", rabbitmq));
    }

    /** 单项状态收敛：true → UP，false / 异常 / 超时 → DOWN。 */
    private String statusOf(String name, Future<Boolean> future) {
        try {
            Boolean up = future.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(up) ? UP : DOWN;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("{} 探活超时（>{} 秒），判定 DOWN", name, PROBE_TIMEOUT_SECONDS);
            return DOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} 探活被中断，判定 DOWN", name);
            return DOWN;
        } catch (Exception e) {
            // ExecutionException 的 message 就是原始异常摘要
            log.warn("{} 探活失败，判定 DOWN：{}", name, e.getMessage());
            return DOWN;
        }
    }

    private boolean checkMysql() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid((int) PROBE_TIMEOUT_SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(brief(e));
        }
    }

    private boolean checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return connection.ping() != null;
        } catch (Exception e) {
            throw new IllegalStateException(brief(e));
        }
    }

    private boolean checkRabbitMq() {
        try (org.springframework.amqp.rabbit.connection.Connection connection =
                     rabbitConnectionFactory.createConnection()) {
            return connection.isOpen();
        } catch (Exception e) {
            throw new IllegalStateException(brief(e));
        }
    }

    /** 异常摘要：只保留消息文本，避免把连接细节带进日志。 */
    private static String brief(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void shutdown() {
        probeExecutor.shutdownNow();
    }
}
