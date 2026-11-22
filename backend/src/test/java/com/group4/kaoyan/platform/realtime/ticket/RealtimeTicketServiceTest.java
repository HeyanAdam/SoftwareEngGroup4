package com.group4.kaoyan.platform.realtime.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 票据服务单测（B-1 验收清单第 1 条：60 秒过期且只能消费一次）。
 *
 * <p>不连真实 Redis：{@code StringRedisTemplate} 用 Mockito 替身，
 * 重点验证"写进去的值对不对、TTL 是不是 60 秒、消费用的是不是原子 GETDEL"。</p>
 */
@ExtendWith(MockitoExtension.class)
class RealtimeTicketServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RealtimeProperties properties;
    private RealtimeTicketService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new RealtimeProperties();
        service = new RealtimeTicketService(redisTemplate, objectMapper, properties);
    }

    @Test
    @DisplayName("签发：票据写入 Redis，值里带 userId 和 chat 通道，TTL 60 秒")
    void issueShouldStoreTicketWithUserAndTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RealtimeTicketService.IssuedTicket issued = service.issueChatTicket(7L);

        assertTrue(issued.ticket().startsWith("rt_"), "票据要带前缀，方便日志/监控识别");
        assertEquals(60, issued.expiresInSeconds());

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq(RealtimeTicketService.KEY_PREFIX + issued.ticket()),
                valueCaptor.capture(),
                eq(Duration.ofSeconds(60)));

        RealtimeTicket payload = objectMapper.readValue(valueCaptor.getValue(), RealtimeTicket.class);
        assertEquals(7L, payload.userId());
        assertEquals(List.of("chat"), payload.allowedChannels());
    }

    @Test
    @DisplayName("消费：用 GETDEL 一次取出并删除，返回原始载荷")
    void consumeShouldUseGetDelAndReturnPayload() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RealtimeTicket stored = new RealtimeTicket(
                7L, List.of("chat"), System.currentTimeMillis(), System.currentTimeMillis() + 60_000);
        when(valueOperations.getAndDelete(RealtimeTicketService.KEY_PREFIX + "rt_abc"))
                .thenReturn(objectMapper.writeValueAsString(stored));

        Optional<RealtimeTicket> result = service.consume("rt_abc");

        assertTrue(result.isPresent());
        assertEquals(7L, result.get().userId());
        // 断言用的是原子删除接口，而不是"先 get 再 delete"（并发下会被用两次）
        verify(valueOperations).getAndDelete(RealtimeTicketService.KEY_PREFIX + "rt_abc");
    }

    @Test
    @DisplayName("消费：同一张票第二次进来，GETDEL 返回 null，判定无效")
    void consumeTwiceShouldBeEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(RealtimeTicketService.KEY_PREFIX + "rt_abc")).thenReturn(null);

        assertTrue(service.consume("rt_abc").isEmpty());
    }

    @Test
    @DisplayName("消费：空票据直接拒绝，不查 Redis")
    void consumeBlankShouldNotTouchRedis() {
        assertTrue(service.consume("  ").isEmpty());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("消费：载荷已过期（即使 Redis 还没清）也判定无效")
    void consumeExpiredPayloadShouldBeEmpty() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RealtimeTicket expired = new RealtimeTicket(
                7L, List.of("chat"), 0L, System.currentTimeMillis() - 1_000);
        when(valueOperations.getAndDelete(RealtimeTicketService.KEY_PREFIX + "rt_old"))
                .thenReturn(objectMapper.writeValueAsString(expired));

        assertTrue(service.consume("rt_old").isEmpty());
    }

    @Test
    @DisplayName("消费：内容坏了按无效处理，不抛异常给握手层")
    void consumeBrokenPayloadShouldBeEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(RealtimeTicketService.KEY_PREFIX + "rt_bad"))
                .thenReturn("{not-json");

        assertTrue(service.consume("rt_bad").isEmpty());
        assertFalse(service.consume(null).isPresent());
    }
}
