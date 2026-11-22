package com.group4.kaoyan.platform.dashboard.service;

import com.group4.kaoyan.platform.messaging.entity.MessageOutbox;
import com.group4.kaoyan.platform.messaging.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Outbox 积压指标（步骤 E3）。
 *
 * <p>统计口径（与 {@code OutboxService} 的重试策略一致）：</p>
 * <pre>
 * outboxPending = PENDING + RETRY_WAIT          待投递，属于正常排队
 * outboxFailed  = FAILED + PUBLISHING 超过 5 分钟  失败，需要人工介入
 * </pre>
 *
 * <p>「PUBLISHING 超过 5 分钟」是实例崩溃留下的孤儿记录，
 * 与 {@code OutboxPublisherJob} 的 {@code resetStuck(now - 5m)} 阈值同源。
 * 本服务只做 COUNT，不读取 {@code payload_json} 正文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxMetricsService {

    /** 卡在 PUBLISHING 的判定阈值：5 分钟。 */
    public static final int STUCK_MINUTES = 5;

    private final OutboxMapper outboxMapper;

    /**
     * Outbox 指标快照。
     *
     * @param up      status 计数是否成功
     * @param pending 待投递条数
     * @param failed  失败条数（含卡住记录）
     */
    public record OutboxMetrics(boolean up, long pending, long failed) {

        /** 查询失败的安全值。 */
        public static OutboxMetrics down() {
            return new OutboxMetrics(false, 0L, 0L);
        }
    }

    /**
     * 查询 Outbox 积压：PENDING / RETRY_WAIT / FAILED / 卡住 PUBLISHING。
     */
    public OutboxMetrics probe() {
        try {
            long pending = outboxMapper.countByStatus(MessageOutbox.STATUS_PENDING)
                    + outboxMapper.countByStatus(MessageOutbox.STATUS_RETRY_WAIT);
            long failed = outboxMapper.countByStatus(MessageOutbox.STATUS_FAILED)
                    + outboxMapper.countStuckPublishing(
                            LocalDateTime.now().minusMinutes(STUCK_MINUTES));
            return new OutboxMetrics(true, pending, failed);
        } catch (Exception e) {
            log.warn("Outbox 指标查询失败，返回安全值：{}", e.getMessage());
            return OutboxMetrics.down();
        }
    }
}
