package com.group4.kaoyan.platform.messaging.service;

import com.group4.kaoyan.common.mybatis.EntityTimestamps;
import com.group4.kaoyan.platform.messaging.entity.ConsumeLog;
import com.group4.kaoyan.platform.messaging.mapper.ConsumeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消费幂等：唯一键 (message_id, consumer_name)。
 *
 * <ul>
 *   <li>首次消费 → NEW，执行业务</li>
 *   <li>已 SUCCEEDED → DONE，直接 ack</li>
 *   <li>PROCESSING 且 10 分钟内 → IN_FLIGHT，说明另一个实例正在处理，ack 掉避免重复</li>
 *   <li>PROCESSING 超过 10 分钟 → 视为上次中断，允许重跑</li>
 *   <li>FAILED → 允许重试</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumeLogService {

    public enum Decision {
        NEW, DONE, IN_FLIGHT
    }

    public record Begin(Decision decision, Long logId, int retryCount) {
    }

    private static final int STALE_MINUTES = 10;

    private final ConsumeLogMapper consumeLogMapper;

    public Begin begin(String messageId, String consumerName, String bizKey) {
        ConsumeLog log = new ConsumeLog();
        log.setMessageId(messageId);
        log.setConsumerName(consumerName);
        log.setBizKey(bizKey);
        log.setStatus(ConsumeLog.STATUS_PROCESSING);
        log.setRetryCount(0);
        EntityTimestamps.fillOnInsert(log);
        try {
            consumeLogMapper.insert(log);
            return new Begin(Decision.NEW, log.getId(), 0);
        } catch (DuplicateKeyException e) {
            ConsumeLog existing = consumeLogMapper.selectByMessage(messageId, consumerName);
            if (existing == null) {
                throw e;
            }
            if (ConsumeLog.STATUS_SUCCEEDED.equals(existing.getStatus())) {
                return new Begin(Decision.DONE, existing.getId(), existing.getRetryCount());
            }
            if (ConsumeLog.STATUS_PROCESSING.equals(existing.getStatus())
                    && existing.getUpdateTime() != null
                    && existing.getUpdateTime().isAfter(LocalDateTime.now().minusMinutes(STALE_MINUTES))) {
                return new Begin(Decision.IN_FLIGHT, existing.getId(), existing.getRetryCount());
            }
            consumeLogMapper.resetProcessing(existing.getId());
            return new Begin(Decision.NEW, existing.getId(),
                    existing.getRetryCount() == null ? 0 : existing.getRetryCount());
        }
    }

    public void succeed(Long logId) {
        consumeLogMapper.markSucceeded(logId);
    }

    /** 记录一次失败（retry_count + 1）。累计次数由调用方用 Begin.retryCount + 1 计算。 */
    public void fail(Long logId, String error) {
        consumeLogMapper.failAndIncrement(logId, brief(error));
    }

    public void deadLetter(Long logId, String error) {
        consumeLogMapper.markDeadLetter(logId, brief(error));
    }

    private static String brief(String error) {
        return error == null ? null : (error.length() <= 1000 ? error : error.substring(0, 1000));
    }
}
