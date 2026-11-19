package com.group4.kaoyan.platform.messaging.job;

import com.group4.kaoyan.platform.messaging.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 投递任务：每 2 秒扫一次待发送消息。
 * 多实例安全由 claim 的条件 UPDATE 保证。
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private final OutboxService outboxService;

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void publish() {
        try {
            int published = outboxService.publishPending();
            if (published > 0) {
                log.debug("Outbox 已投递 {} 条消息", published);
            }
        } catch (Exception e) {
            log.error("Outbox 投递任务失败", e);
        }
    }
}
