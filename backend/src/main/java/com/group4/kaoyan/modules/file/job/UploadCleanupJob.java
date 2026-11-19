package com.group4.kaoyan.modules.file.job;

import com.group4.kaoyan.modules.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期上传会话清理：每 10 分钟扫一次 INIT/UPLOADING 且 expires_at 已过的会话。
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class UploadCleanupJob {

    private final FileService fileService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void cleanup() {
        try {
            int handled = fileService.cleanupExpiredSessions(100);
            if (handled > 0) {
                log.info("已清理过期上传会话 {} 个", handled);
            }
        } catch (Exception e) {
            log.error("上传会话清理任务失败", e);
        }
    }
}
