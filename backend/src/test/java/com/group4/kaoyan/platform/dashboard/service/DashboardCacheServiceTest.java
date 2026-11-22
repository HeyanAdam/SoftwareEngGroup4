package com.group4.kaoyan.platform.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardCardsVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 步骤 E1 的单元测试：key 规则、TTL、命中/未命中、Redis 故障降级。
 *
 * <p>不依赖真实 Redis：{@link StringRedisTemplate} 用 Mockito 打桩。</p>
 */
@DisplayName("大屏 Redis 缓存（E1）")
class DashboardCacheServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DashboardCacheService cacheService =
            new DashboardCacheService(redisTemplate, objectMapper);

    DashboardCacheServiceTest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private AdminDashboardSummaryVO summary(long totalUsers) {
        AdminDashboardSummaryVO summary = new AdminDashboardSummaryVO();
        DashboardCardsVO cards = DashboardCardsVO.empty();
        cards.setTotalUsers(totalUsers);
        summary.setCards(cards);
        summary.setTrend(new DashboardTrendVO());
        summary.setSystem(DashboardSystemVO.down());
        summary.setGeneratedAt("2026-11-22T10:00:00+08:00");
        return summary;
    }

    @Test
    @DisplayName("命中缓存：直接反序列化返回，不再统计")
    void getReturnsCachedSummary() throws Exception {
        AdminDashboardSummaryVO source = summary(120L);
        when(valueOperations.get(DashboardCacheService.keyOf(7)))
                .thenReturn(objectMapper.writeValueAsString(source));

        Optional<AdminDashboardSummaryVO> hit = cacheService.get(7);

        assertTrue(hit.isPresent());
        assertNotNull(hit.get().getCards());
        assertEquals(120L, hit.get().getCards().getTotalUsers());
        assertEquals("2026-11-22T10:00:00+08:00", hit.get().getGeneratedAt());
    }

    @Test
    @DisplayName("未命中：返回 empty，不抛异常")
    void getReturnsEmptyOnMiss() {
        when(valueOperations.get(DashboardCacheService.keyOf(14))).thenReturn(null);
        assertTrue(cacheService.get(14).isEmpty());
    }

    @Test
    @DisplayName("写入缓存：key 精确、TTL 固定 15 秒")
    void putUsesExactKeyAndFifteenSecondTtl() throws Exception {
        cacheService.put(30, summary(10L));

        verify(valueOperations).set(
                eq("admin:dashboard:summary:v1:30"),
                anyString(),
                eq(Duration.ofSeconds(15)));
    }

    @Test
    @DisplayName("不同 days 使用不同 key，互不串数据")
    void differentDaysUseDifferentKeys() {
        cacheService.get(7);
        cacheService.get(30);

        verify(valueOperations).get("admin:dashboard:summary:v1:7");
        verify(valueOperations).get("admin:dashboard:summary:v1:30");
    }

    @Test
    @DisplayName("Redis 无响应：单次缓存 IO 超 300ms 即降级，接口不被拖住")
    void getDegradesFastWhenRedisHangs() {
        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return null;
        });

        long start = System.currentTimeMillis();
        assertTrue(cacheService.get(7).isEmpty());
        long cost = System.currentTimeMillis() - start;

        assertTrue(cost < 1000, "缓存超时应在 300ms 量级，实际耗时 " + cost + "ms");
    }

    @Test
    @DisplayName("Redis 不可用：读取与写入都降级，不抛异常")
    void redisFailureDegradesSilently() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis 连接失败"));

        assertTrue(cacheService.get(7).isEmpty());

        org.mockito.Mockito.doThrow(new RuntimeException("redis 连接失败"))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(15)));
        // 写入失败只记日志：能走到这里即未向上抛出异常
        cacheService.put(7, summary(1L));
    }
}
