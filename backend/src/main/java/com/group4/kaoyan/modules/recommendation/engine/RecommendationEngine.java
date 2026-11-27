package com.group4.kaoyan.modules.recommendation.engine;

import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则式推荐引擎。不调 LLM,规则失败返回空数组。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationEngine {

    private final StudyTaskMapper studyTaskMapper;
    private final Clock studyClock;

    /**
     * 今日场景推荐:逾期未完成 > 高优先级 TODO > 薄弱知识点 > 即将截止。
     */
    public List<UserRecommendation> generateTODAY(Long userId, LocalDate today) {
        LocalDateTime now = LocalDateTime.now(studyClock);
        List<StudyTask> tasks = studyTaskMapper.selectByUserAndDate(userId, today);
        List<UserRecommendation> result = new ArrayList<>();

        // 逾期未完成 → 高优先级
        for (StudyTask t : tasks) {
            if (isOverdue(t, now)) {
                result.add(buildRec(userId, "TODAY", "NONE", null, "STUDY_TASK",
                        t.getTitle(), "该任务已逾期,建议尽快完成",
                        "CREATE_TASK", "加入今日计划", null,
                        new BigDecimal("0.95"), t.getEstimatedMinutes(),
                        t.getSubject(), t.getKnowledgePoint(), "TODAY:" + userId + ":OVERDUE:" + t.getId()));
            }
        }

        // 高优先级 TODO
        List<StudyTask> highPriority = tasks.stream()
                .filter(t -> "TODO".equals(t.getStatus()) && !isOverdue(t, now))
                .filter(t -> t.getPriority() != null && t.getPriority() >= 70)
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .limit(Math.max(0, 3 - result.size()))
                .toList();
        for (StudyTask t : highPriority) {
            result.add(buildRec(userId, "TODAY", "NONE", null, "STUDY_TASK",
                    "优先完成: " + t.getTitle(), "高优先级任务,建议集中处理",
                    "CREATE_TASK", "立即开始", null,
                    new BigDecimal("0.80"), t.getEstimatedMinutes(),
                    t.getSubject(), t.getKnowledgePoint(), "TODAY:" + userId + ":HIGH:" + t.getId()));
        }

        return result.stream().limit(3).toList();
    }

    /**
     * 任务完成场景推荐:同计划下一未完成 > 同科/薄弱点。
     */
    public List<UserRecommendation> generateTASK_COMPLETE(Long userId, Long taskId) {
        List<UserRecommendation> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(studyClock);

        StudyTask source = studyTaskMapper.selectById(taskId);
        if (source == null) return result;

        // 同计划未完成
        List<StudyTask> siblings = studyTaskMapper.selectByPlanId(source.getPlanId()).stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) && !"CANCELLED".equals(t.getStatus()))
                .filter(t -> !t.getId().equals(taskId))
                .toList();

        if (!siblings.isEmpty()) {
            StudyTask next = siblings.get(0);
            result.add(buildRec(userId, "TASK_COMPLETE", "TASK", taskId, "STUDY_TASK",
                    next.getTitle(), "趁热打铁,继续下一个任务",
                    "CREATE_TASK", "继续学习", null,
                    new BigDecimal("0.90"), next.getEstimatedMinutes(),
                    next.getSubject(), next.getKnowledgePoint(),
                    "TASK_COMPLETE:" + userId + ":" + taskId + ":NEXT:" + next.getId()));
        }

        // 同科薄弱点任务
        List<StudyTask> sameSubject = studyTaskMapper.selectByUserAndDate(userId, source.getPlanDate()).stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) && !"CANCELLED".equals(t.getStatus()))
                .filter(t -> source.getSubject() != null && source.getSubject().equals(t.getSubject()))
                .filter(t -> !t.getId().equals(taskId))
                .filter(t -> !siblings.contains(t))
                .limit(Math.max(0, 2 - result.size()))
                .toList();
        for (StudyTask t : sameSubject) {
            result.add(buildRec(userId, "TASK_COMPLETE", "TASK", taskId, "STUDY_TASK",
                    t.getTitle(), "同科任务,趁感觉好时继续",
                    "CREATE_TASK", "加入任务", null,
                    new BigDecimal("0.75"), t.getEstimatedMinutes(),
                    t.getSubject(), t.getKnowledgePoint(),
                    "TASK_COMPLETE:" + userId + ":" + taskId + ":SUBJECT:" + t.getId()));
        }

        return result.stream().limit(3).toList();
    }

    /**
     * 聊天场景推荐(简化版): 返回空列表。规则实现后再填充。
     */
    public List<UserRecommendation> generateCHAT(Long userId, Long contextId) {
        // 简化:规则实现后填充。此处避免空指针。
        return Collections.emptyList();
    }

    // ===== 辅助 =====

    private boolean isOverdue(StudyTask t, LocalDateTime now) {
        return !"COMPLETED".equals(t.getStatus())
                && !"CANCELLED".equals(t.getStatus())
                && t.getDueAt() != null
                && t.getDueAt().isBefore(now);
    }

    private UserRecommendation buildRec(Long userId, String scene, String contextType, Long contextId,
                                        String type, String title, String reason, String actionType,
                                        String actionLabel, String actionPath, BigDecimal score,
                                        Integer estimatedMinutes, String subject, String knowledgePoint,
                                        String dedupKey) {
        LocalDateTime now = LocalDateTime.now(studyClock);
        UserRecommendation rec = new UserRecommendation();
        rec.setUserId(userId);
        rec.setScene(scene);
        rec.setContextType(contextType);
        rec.setContextId(contextId);
        rec.setType(type);
        rec.setTitle(title);
        rec.setReason(reason);
        rec.setActionType(actionType);
        rec.setActionLabel(actionLabel);
        rec.setActionPath(actionPath);
        rec.setScore(score);
        rec.setEstimatedMinutes(estimatedMinutes);
        rec.setSubject(subject);
        rec.setKnowledgePoint(knowledgePoint);
        rec.setStatus("ACTIVE");
        rec.setAcceptedTaskId(null);
        rec.setDedupKey(dedupKey);
        rec.setExpiresAt(now.plusDays(1));
        rec.setCreateTime(now);
        rec.setUpdateTime(now);
        return rec;
    }
}
