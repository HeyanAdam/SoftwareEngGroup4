package com.group4.kaoyan.platform.messaging.service;

import com.group4.kaoyan.platform.messaging.entity.MessageOutbox;
import com.group4.kaoyan.platform.messaging.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 投递：抢单 → 发送 → publisher-confirm 通过才置 PUBLISHED。
 *
 * <p>多实例安全由 claim 的条件 UPDATE 保证：只有把 PENDING/RETRY_WAIT 改成 PUBLISHING
 * 的那个实例才会真正发送。失败按指数退避重试，超过 5 次置 FAILED。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;
    private static final int STUCK_MINUTES = 5;

    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    /** 取出并投递一批待发送消息，返回成功条数。 */
    public int publishPending() {
        // 先把卡在 PUBLISHING 的记录复位（实例崩溃兜底），重复执行无副作用
        outboxMapper.resetStuck(LocalDateTime.now().minusMinutes(STUCK_MINUTES));

        List<MessageOutbox> pending = outboxMapper.selectPending(BATCH_SIZE);
        int published = 0;
        for (MessageOutbox outbox : pending) {
            if (outboxMapper.claim(outbox.getId()) == 0) {
                // 被其他实例抢走
                continue;
            }
            if (send(outbox)) {
                published++;
            }
        }
        return published;
    }

    private boolean send(MessageOutbox outbox) {
        try {
            CorrelationData correlation = new CorrelationData(outbox.getEventId());
            Object payload = outbox.getPayloadJson();
            rabbitTemplate.convertAndSend(
                    outbox.getExchangeName(),
                    outbox.getRoutingKey(),
                    payload,
                    message -> {
                        message.getMessageProperties().setMessageId(outbox.getEventId());
                        message.getMessageProperties().setContentType("application/json");
                        return message;
                    },
                    correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("broker 未确认：" + (confirm == null ? "超时" : confirm.getReason()));
            }
            outboxMapper.markPublished(outbox.getId());
            return true;
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            int nextRetry = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
            if (nextRetry > MAX_RETRY) {
                outboxMapper.markFailed(outbox.getId(), brief(error));
                log.error("Outbox 消息投递失败超过 {} 次，置 FAILED eventId={}", MAX_RETRY, outbox.getEventId());
            } else {
                long backoff = Math.min(30L * (1L << Math.min(nextRetry, 5)), 600L);
                outboxMapper.markRetry(outbox.getId(), brief(error), LocalDateTime.now().plusSeconds(backoff));
                log.warn("Outbox 投递失败，{} 秒后重试 eventId={}：{}", backoff, outbox.getEventId(), error);
            }
            return false;
        }
    }

    private static String brief(String error) {
        return error == null ? null : (error.length() <= 1000 ? error : error.substring(0, 1000));
    }
}
