package com.group4.kaoyan.platform.messaging.service;

import com.group4.kaoyan.common.mybatis.EntityTimestamps;
import com.group4.kaoyan.common.mybatis.PageQuery;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import com.group4.kaoyan.platform.messaging.entity.AsyncTask;
import com.group4.kaoyan.platform.messaging.entity.MessageOutbox;
import com.group4.kaoyan.platform.messaging.mapper.AsyncTaskMapper;
import com.group4.kaoyan.platform.messaging.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 异步任务状态机 + 文档解析任务创建。
 *
 * <p>状态流转：PENDING → RUNNING → SUCCEEDED；失败可 RETRY_WAIT 重试，超限 FAILED。
 * 所有状态更新都是条件 UPDATE，影响 0 行说明被取消或已被其他实例处理，直接返回。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskService {

    private static final DateTimeFormatter TASK_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_RETRY = 5;
    private static final String MESSAGE_TYPE_DOC_PARSE = "DOC_PARSE_TASK";

    private final AsyncTaskMapper taskMapper;
    private final OutboxMapper outboxMapper;

    /**
     * 文档解析任务：任务表 + Outbox 同一本地事务写入。
     * MQ 发送不在事务内，由 OutboxPublisherJob 负责投递。
     */
    @Transactional(rollbackFor = Exception.class)
    public AsyncTask createDocParseTask(Long userId, Long docId, String fileName, String objectKey) {
        AsyncTask task = new AsyncTask();
        task.setTaskNo(buildTaskNo(AsyncTask.TYPE_DOC_PARSE));
        task.setTaskType(AsyncTask.TYPE_DOC_PARSE);
        task.setBizType(AsyncTask.BIZ_DOCUMENT);
        task.setBizId(docId);
        task.setUserId(userId);
        task.setStatus(AsyncTask.STATUS_PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("fileName", fileName);
        payload.put("fileUrl", objectKey);
        task.setPayloadJson(payload);
        EntityTimestamps.fillOnInsert(task);
        taskMapper.insert(task);

        DocParseTaskDTO message = new DocParseTaskDTO();
        message.setDocId(docId);
        message.setFileName(fileName);
        message.setFileUrl(objectKey);

        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(UUID.randomUUID().toString().replace("-", ""));
        outbox.setExchangeName(RabbitTopologyConfig.EXCHANGE);
        outbox.setRoutingKey(RabbitTopologyConfig.DOC_PARSE_ROUTING_KEY);
        outbox.setMessageType(MESSAGE_TYPE_DOC_PARSE);
        outbox.setBizKey("DOC:" + docId);
        outbox.setPayloadJson(message);
        outbox.setStatus(MessageOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        EntityTimestamps.fillOnInsert(outbox);
        outboxMapper.insert(outbox);
        log.info("文档解析任务已创建 taskNo={} docId={}", task.getTaskNo(), docId);
        return task;
    }

    public AsyncTask getById(Long id) {
        return taskMapper.selectById(id);
    }

    public R.PageData<AsyncTask> page(String taskType, String status, long pageNum, long pageSize) {
        PageQuery page = PageQuery.of(pageNum, pageSize);
        long total = taskMapper.countPage(taskType, status);
        List<AsyncTask> records = taskMapper.selectPage(taskType, status, page.getOffset(), page.getLimit());
        return R.PageData.of(total, records);
    }

    /** 把文档对应任务置 RUNNING，返回任务 id；没有可执行任务时返回 null。 */
    public Long markRunningByDoc(Long docId) {
        AsyncTask task = taskMapper.selectLatestByBiz(AsyncTask.BIZ_DOCUMENT, docId);
        if (task == null) {
            return null;
        }
        if (taskMapper.markRunning(task.getId(), 0) == 0) {
            log.debug("任务状态不可执行，跳过任务标记 docId={} status={}", docId, task.getStatus());
            return task.getId();
        }
        return task.getId();
    }

    public void succeed(Long taskId, Object result) {
        if (taskId == null) {
            return;
        }
        taskMapper.finish(taskId, AsyncTask.STATUS_SUCCEEDED, result, null);
    }

    public void fail(Long taskId, String error) {
        if (taskId == null) {
            return;
        }
        taskMapper.finish(taskId, AsyncTask.STATUS_FAILED, null, brief(error));
    }

    /** 失败后按指数退避安排重试；超过上限置 FAILED。返回是否还会重试。 */
    public boolean retryOrFail(Long taskId, String error) {
        if (taskId == null) {
            return false;
        }
        AsyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (nextRetry > MAX_RETRY) {
            fail(taskId, error);
            return false;
        }
        long backoffSeconds = Math.min(30L * (1L << Math.min(nextRetry, 5)), 600L);
        taskMapper.scheduleRetry(taskId, brief(error), LocalDateTime.now().plusSeconds(backoffSeconds));
        return true;
    }

    public boolean cancel(Long taskId) {
        return taskMapper.cancel(taskId) > 0;
    }

    /** 按文档定位最近任务后安排重试（消费者使用）。 */
    public boolean retryOrFailByDoc(Long docId, String error) {
        AsyncTask task = taskMapper.selectLatestByBiz(AsyncTask.BIZ_DOCUMENT, docId);
        return task != null && retryOrFail(task.getId(), error);
    }

    public void failByDoc(Long docId, String error) {
        AsyncTask task = taskMapper.selectLatestByBiz(AsyncTask.BIZ_DOCUMENT, docId);
        if (task != null) {
            fail(task.getId(), error);
        }
    }

    /** 卡在 RUNNING 超过 10 分钟的任务回收成 RETRY_WAIT，由下次消费重跑。 */
    public int recoverStaleTasks() {
        List<AsyncTask> stale = taskMapper.selectStaleRunning(LocalDateTime.now().minusMinutes(10), 100);
        int recovered = 0;
        for (AsyncTask task : stale) {
            if (taskMapper.recover(task.getId()) > 0) {
                recovered++;
            }
        }
        return recovered;
    }

    private static String buildTaskNo(String type) {
        return type + "-" + LocalDateTime.now().format(TASK_NO_FORMAT) + "-"
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private static String brief(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
