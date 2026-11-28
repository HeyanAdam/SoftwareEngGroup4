package com.group4.kaoyan.modules.studyplan.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.recommendation.engine.RecommendationEngine;
import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import com.group4.kaoyan.modules.recommendation.mapper.RecommendationMapper;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskUpdateRequest;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskWriteRequest;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 学习任务 Service。乐观锁条件 UPDATE,成功后若任务完成则触发推荐生成和 WebSocket 推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyTaskService {

    private final StudyTaskMapper studyTaskMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationEngine recommendationEngine;
    private final RealtimeDeliveryPort delivery;
    private final StudyPlanService studyPlanService;
    private final Clock studyClock;

    @Transactional
    public StudyTaskVO create(Long userId, StudyTaskWriteRequest req) {
        validateWriteRequest(req, false);
        LocalDate today = LocalDate.now(studyClock);
        LocalDateTime now = LocalDateTime.now(studyClock);

        StudyTask task = new StudyTask();
        task.setPlanId(studyPlanService.ensureTodayPlan(userId).getId());
        task.setUserId(userId);
        task.setPlanDate(today);
        applyEditableFields(task, req);
        task.setStatus("TODO");
        task.setVersion(0);
        task.setCompletedAt(null);
        task.setDeleted(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        studyTaskMapper.insert(task);

        StudyTask created = studyTaskMapper.selectById(task.getId());
        return StudyPlanService.toTaskVO(created == null ? task : created, now);
    }

    @Transactional
    public StudyTaskVO updateDetails(Long userId, Long taskId, StudyTaskWriteRequest req) {
        validateWriteRequest(req, true);
        StudyTask existing = requireOwned(taskId, userId);
        int expectedVersion = req.expectedVersion();

        StudyTask update = new StudyTask();
        update.setId(taskId);
        update.setUserId(userId);
        applyEditableFields(update, req);
        if (studyTaskMapper.updateDetailsWithVersion(update, expectedVersion) == 0) {
            throw conflictOrNotFound(userId, taskId);
        }

        StudyTask saved = studyTaskMapper.selectById(taskId);
        return StudyPlanService.toTaskVO(saved == null ? existing : saved,
                LocalDateTime.now(studyClock));
    }

    @Transactional
    public void delete(Long userId, Long taskId, int expectedVersion) {
        requireOwned(taskId, userId);
        if (studyTaskMapper.logicDelete(taskId, userId, expectedVersion) == 0) {
            throw conflictOrNotFound(userId, taskId);
        }
    }

    @Transactional
    public StudyTaskVO updateStatus(Long userId, Long taskId, StudyTaskUpdateRequest req) {
        // 1. 查询
        StudyTask task = studyTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }

        // 2. 归属
        if (!task.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该任务");
        }

        // 3. 把目标状态写到实体上再走乐观锁条件 UPDATE（Mapper 读取 #{task.status}）
        task.setStatus(req.status());
        int affected = studyTaskMapper.updateWithVersion(task, req.expectedVersion());
        if (affected == 0) {
            // 重查确认原因
            StudyTask fresh = studyTaskMapper.selectById(taskId);
            if (fresh == null) throw new BizException(404, "任务不存在");
            if (!fresh.getUserId().equals(userId)) throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该任务");
            // 版本不匹配
            throw new BizException(409, "任务已被修改, 请刷新后重试");
        }

        // 4. 返回最新
        StudyTask updated = studyTaskMapper.selectById(taskId);
        StudyTaskVO vo = StudyPlanService.toTaskVO(updated, LocalDateTime.now(studyClock));

        // 5. 事务提交后触发推荐生成和推送
        if ("COMPLETED".equals(req.status())) {
            afterCommit(() -> {
                try {
                    generateAndPushRecommendation(userId, taskId);
                } catch (Exception e) {
                    log.warn("推荐推送失败, userId={}, taskId={}", userId, taskId, e);
                }
            });
        }

        return vo;
    }

    private StudyTask requireOwned(Long taskId, Long userId) {
        StudyTask task = studyTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该任务");
        }
        return task;
    }

    private BizException conflictOrNotFound(Long userId, Long taskId) {
        StudyTask fresh = studyTaskMapper.selectById(taskId);
        if (fresh == null) {
            return new BizException(404, "任务不存在");
        }
        if (!fresh.getUserId().equals(userId)) {
            return new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该任务");
        }
        return new BizException(409, "任务已被修改, 请刷新后重试");
    }

    private void validateWriteRequest(StudyTaskWriteRequest req, boolean requireVersion) {
        if (req == null) {
            throw new BizException("任务参数不能为空");
        }
        if (req.title() == null || req.title().isBlank()) {
            throw new BizException("任务标题不能为空");
        }
        if (req.title().trim().length() > 120) {
            throw new BizException("任务标题不能超过 120 个字符");
        }
        if (req.subject() == null || req.subject().isBlank()) {
            throw new BizException("任务科目不能为空");
        }
        if (req.subject().trim().length() > 30) {
            throw new BizException("任务科目不能超过 30 个字符");
        }
        if (req.knowledgePoint() != null && req.knowledgePoint().trim().length() > 100) {
            throw new BizException("知识点不能超过 100 个字符");
        }
        if (req.estimatedMinutes() == null
                || req.estimatedMinutes() < 1
                || req.estimatedMinutes() > 600) {
            throw new BizException("预计学习时长必须在 1～600 分钟之间");
        }
        if (req.priority() != null && (req.priority() < 0 || req.priority() > 100)) {
            throw new BizException("任务优先级必须在 0～100 之间");
        }
        if (requireVersion && (req.expectedVersion() == null || req.expectedVersion() < 0)) {
            throw new BizException("expectedVersion 必须 >= 0");
        }
    }

    private void applyEditableFields(StudyTask task, StudyTaskWriteRequest req) {
        task.setTitle(req.title().trim());
        task.setSubject(req.subject().trim());
        task.setKnowledgePoint(trimToNull(req.knowledgePoint()));
        task.setEstimatedMinutes(req.estimatedMinutes());
        task.setDueAt(req.dueAt());
        task.setPriority(req.priority() == null ? 50 : req.priority());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void generateAndPushRecommendation(Long userId, Long taskId) {
        List<UserRecommendation> recs = recommendationEngine.generateTASK_COMPLETE(userId, taskId);
        for (UserRecommendation rec : recs) {
            recommendationMapper.insert(rec);
        }
        String eventId = UUID.randomUUID().toString().replace("-", "");
        String nowText = LocalDateTime.now(studyClock).atOffset(ZoneOffset.ofHours(8)).toString();
        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "scene", "TASK_COMPLETE",
                "contextType", "TASK",
                "contextId", taskId,
                "items", recs);
        delivery.pushToUser(userId, new RealtimeOutboundMessage(
                RealtimeProtocol.TYPE_RECOMMENDATION_UPDATED,
                "push-rec-" + eventId,
                null, null, nowText, payload));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            // 无事务上下文(如单元测试):直接执行,不阻塞主流程
            action.run();
        }
    }
}
