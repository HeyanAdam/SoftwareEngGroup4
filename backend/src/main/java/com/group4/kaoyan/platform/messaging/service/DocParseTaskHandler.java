package com.group4.kaoyan.platform.messaging.service;

import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.group4.kaoyan.modules.knowledge.service.DocumentProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 文档解析消息的业务处理：任务置 RUNNING → 流水线 → 任务 SUCCEEDED。
 * 三步在同一事务内，流水线抛异常时任务状态一并回滚，由消费者记录失败并重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocParseTaskHandler {

    private final AsyncTaskService asyncTaskService;
    private final DocumentProcessService documentProcessService;

    @Transactional(rollbackFor = Exception.class)
    public void handle(DocParseTaskDTO task) {
        if (task == null || task.getDocId() == null) {
            throw new IllegalArgumentException("文档解析消息缺少 docId");
        }
        Long taskId = asyncTaskService.markRunningByDoc(task.getDocId());
        documentProcessService.processInTransaction(task.getDocId());
        asyncTaskService.succeed(taskId, Map.of("docId", task.getDocId()));
        log.info("文档解析消息处理完成 docId={}", task.getDocId());
    }
}
