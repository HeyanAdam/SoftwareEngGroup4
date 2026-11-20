package com.group4.kaoyan.platform.messaging.job;

import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异步任务回收：RUNNING 超过 10 分钟（实例崩溃/消息丢失）拉回 RETRY_WAIT。
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AsyncTaskRecoveryJob {

    private final AsyncTaskService asyncTaskService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recover() {
        try {
            int recovered = asyncTaskService.recoverStaleTasks();
            if (recovered > 0) {
                log.info("已回收卡死的异步任务 {} 个", recovered);
            }
        } catch (Exception e) {
            log.error("异步任务回收失败", e);
        }
    }
}
