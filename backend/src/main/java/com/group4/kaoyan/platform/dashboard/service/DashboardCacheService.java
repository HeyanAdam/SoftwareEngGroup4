package com.group4.kaoyan.platform.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 大屏统计短时缓存（步骤 E1）。
 *
 * <p>三条硬约定：</p>
 * <ol>
 *   <li><b>Key</b>：{@code admin:dashboard:summary:v1:{days}}——带版本号，便于口径调整时整批失效；
 *       不同 days 各有独立 key，7/14/30 切换不会互相串数据。</li>
 *   <li><b>TTL 固定 15 秒</b>，与前端 15 秒轮询同频；永不写无 TTL 的永久 key。</li>
 *   <li><b>失败即降级</b>：读/写/反序列化任何一步异常都只记 warn，然后当作未命中，
 *       由调用方走真实统计；Redis 抖动不会让大屏 500。</li>
 * </ol>
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

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 按天数拼 key：{@code admin:dashboard:summary:v1:7}。 */
    public static String keyOf(int days) {
        return KEY_PREFIX + days;
    }

    /**
     * 读取缓存。命中返回反序列化对象；未命中或任何异常都返回 {@link Optional#empty()}。
     */
    public Optional<AdminDashboardSummaryVO> get(int days) {
        String key = keyOf(days);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, AdminDashboardSummaryVO.class));
        } catch (Exception e) {
            log.warn("大屏缓存读取失败，降级为直接统计 key={}：{}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写入缓存，TTL 15 秒。失败只记日志，不影响本次响应。
     */
    public void put(int days, AdminDashboardSummaryVO summary) {
        String key = keyOf(days);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(summary), TTL);
        } catch (Exception e) {
            log.warn("大屏缓存写入失败，本次响应照常返回 key={}：{}", key, e.getMessage());
        }
    }
}
