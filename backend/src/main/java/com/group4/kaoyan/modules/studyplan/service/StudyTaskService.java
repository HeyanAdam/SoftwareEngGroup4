package com.group4.kaoyan.modules.studyplan.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.recommendation.engine.RecommendationEngine;
import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import com.group4.kaoyan.modules.recommendation.mapper.RecommendationMapper;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskUpdateRequest;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
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
    private final Clock studyClock;

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

        // 3. 乐观锁条件 UPDATE
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
