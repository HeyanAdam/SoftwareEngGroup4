package com.group4.kaoyan.platform.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 大屏统计短时缓存（步骤 E1）。
 *
 * <p>三条硬约定：</p>
 * <ol>
 *   <li><b>Key</b>：{@code admin:dashboard:summary:v1:{days}}——带版本号，便于口径调整时整批失效；
 *       不同 days 各有独立 key，7/14/30 切换不会互相串数据。</li>
 *   <li><b>TTL 固定 15 秒</b>，与前端 15 秒轮询同频；永不写无 TTL 的永久 key。</li>
 *   <li><b>失败即降级</b>：读/写/反序列化任何一步异常或超时都只记 warn，然后当作未命中，
 *       由调用方走真实统计；Redis 抖动不会让大屏 500。</li>
 * </ol>
 *
 * <p>为什么每次缓存操作都放在独立线程里加 300ms 上限：Spring Data Redis 的命令超时
 * 是全局 5 秒（{@code spring.data.redis.timeout}）。Redis 进程被停掉时，一次 GET + 一次 SET
 * 会把接口拖到 10 秒以上（实测 13.7 秒），前端看起来就是"卡住"。这里给缓存 IO 单独设
 * 300ms 上限，超时即放弃本次缓存，接口整体耗时收敛到探活的 2 秒量级。</p>
 *
 * <p>缓存里只放聚合计数与状态字符串（AdminDashboardSummaryVO），
 * 不会写入聊天正文、邮箱或任何凭据；也不执行 {@code KEYS *}，只按精确 key 存取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardCacheService {

    /** 缓存 key 前缀，v1 为版本段。 */
    public static final String KEY_PREFIX = "admin:dashboard:summary:v1:";

    /** 缓存有效期：15 秒，与轮询周期一致。 */
    public static final Duration TTL = Duration.ofSeconds(15);

    /** 单次缓存 IO 上限：300 毫秒；超过即放弃，走真实统计。 */
    public static final long IO_TIMEOUT_MILLIS = 300L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 缓存 IO 专用线程池：守护线程，Redis 卡住时不阻塞停机。 */
    private final ExecutorService cacheExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "dashboard-cache-io");
        thread.setDaemon(true);
        return thread;
    });

    /** 按天数拼 key：{@code admin:dashboard:summary:v1:7}。 */
    public static String keyOf(int days) {
        return KEY_PREFIX + days;
    }

    /**
     * 读取缓存。命中返回反序列化对象；未命中、异常或超过 300ms 都返回 {@link Optional#empty()}。
     */
    public Optional<AdminDashboardSummaryVO> get(int days) {
        String key = keyOf(days);
        Future<String> future = cacheExecutor.submit(() -> redisTemplate.opsForValue().get(key));
        try {
            String json = future.get(IO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, AdminDashboardSummaryVO.class));
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("大屏缓存读取超时（>{}ms），降级为直接统计 key={}", IO_TIMEOUT_MILLIS, key);
            return Optional.empty();
        } catch (Exception e) {
            future.cancel(true);
            log.warn("大屏缓存读取失败，降级为直接统计 key={}：{}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写入缓存，TTL 15 秒。失败或超时只记日志，不影响本次响应。
     */
    public void put(int days, AdminDashboardSummaryVO summary) {
        String key = keyOf(days);
        Future<?> future = cacheExecutor.submit(() -> {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(summary), TTL);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
        try {
            future.get(IO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("大屏缓存写入超时（>{}ms），本次响应照常返回 key={}", IO_TIMEOUT_MILLIS, key);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("大屏缓存写入失败，本次响应照常返回 key={}：{}", key, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        cacheExecutor.shutdownNow();
    }
}
