package com.group4.kaoyan.platform.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 幂等记录清理：每天 03:00 删除过期记录，单次最多 1000 条，最多循环 10 轮。
 * 重复执行无副作用。
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private final IdempotencyService idempotencyService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        int deleted;
        try {
            deleted = idempotencyService.cleanup();
        } catch (Exception e) {
            log.error("幂等记录清理失败", e);
            return;
        }
        if (deleted > 0) {
            log.info("已清理过期幂等记录 {} 条", deleted);
        }
    }
}
