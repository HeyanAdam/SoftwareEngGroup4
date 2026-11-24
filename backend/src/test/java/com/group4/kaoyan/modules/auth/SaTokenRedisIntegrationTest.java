package com.group4.kaoyan.modules.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Redis 集成测试：验证 Sa-Token 使用 Redis DAO，而不是默认内存 DAO。
 *
 * <p>普通 CI 不要求 Redis，因此默认跳过。本地执行：</p>
 * <pre>
 * $env:SA_TOKEN_REDIS_IT = "true"
 * mvn -Dtest=SaTokenRedisIntegrationTest test
 * </pre>
 */
@SpringBootTest(
        classes = SaTokenRedisIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.web-application-type=none",
                "spring.flyway.enabled=false",
                "spring.rabbitmq.listener.simple.auto-startup=false"
        })
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SA_TOKEN_REDIS_IT", matches = "true")
class SaTokenRedisIntegrationTest {

    private static final String KEY_PREFIX = "kaoyan:test:sa-token:";

    @Autowired
    private SaTokenDao saTokenDao;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Sa-Token DAO 使用 Redis 且会话数据带 TTL")
    void saTokenDaoPersistsToRedisWithTtl() {
        assertInstanceOf(SaTokenDaoRedisJackson.class, SaManager.getSaTokenDao(),
                "Sa-Token 仍在使用默认内存 DAO");

        String key = KEY_PREFIX + UUID.randomUUID();
        String value = "redis-session-" + UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(60);

        try {
            saTokenDao.set(key, value, ttl.toSeconds());

            assertEquals(value, saTokenDao.get(key));
            assertEquals(value, redisTemplate.opsForValue().get(key));

            Long redisTtl = redisTemplate.getExpire(key);
            assertNotNull(redisTtl);
            assertTrue(redisTtl > 0 && redisTtl <= ttl.toSeconds(),
                    "Redis 会话键没有正确设置 TTL：" + redisTtl);
        } finally {
            saTokenDao.delete(key);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
