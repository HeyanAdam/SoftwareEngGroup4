package com.group4.kaoyan.modules.recommendation.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.StudyTimeUtils;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.recommendation.dto.*;
import com.group4.kaoyan.modules.recommendation.engine.RecommendationEngine;
import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import com.group4.kaoyan.modules.recommendation.mapper.RecommendationMapper;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.modules.studyplan.service.StudyPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 推荐 Service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationMapper recommendationMapper;
    private final RecommendationEngine recommendationEngine;
    private final StudyTaskMapper studyTaskMapper;
    private final StudyPlanService studyPlanService;
    private final ChatService chatService;
    private final Clock studyClock;

    private static final List<String> VALID_SCENES = List.of("TODAY", "CHAT", "TASK_COMPLETE");
    private static final List<String> VALID_CONTEXTS = List.of("NONE", "MESSAGE", "TASK", "CONVERSATION");

    /**
     * 查询推荐。首次无缓存时按需生成。
     */
    public RecommendationListVO list(Long userId, String scene, String contextType,
                                      Long contextId, Integer limit) {
        String normalizedContextType = contextType == null ? "NONE" : contextType;
        // 参数校验
        if (!VALID_SCENES.contains(scene)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scene 必须是 TODAY / CHAT / TASK_COMPLETE 之一");
        }
        if (!VALID_CONTEXTS.contains(normalizedContextType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "contextType 不合法");
        }
        if (!"NONE".equals(normalizedContextType) && contextId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "contextType 非 NONE 时 contextId 必填");
        }
        int realLimit = (limit == null ? 3 : Math.min(limit, 10));

        // 上下文归属校验：上下文不存在 404；存在但不属于当前用户 10009
        validateContextOwnership(userId, normalizedContextType, contextId);

        LocalDateTime now = LocalDateTime.now(studyClock);

        // 查缓存
        List<UserRecommendation> existing = recommendationMapper.selectActiveByUserScene(userId, scene, now);
        if (existing.isEmpty()) {
            // 首次生成
            List<UserRecommendation> generated = generateByScene(userId, scene, contextId);
            for (UserRecommendation rec : generated) {
                try {
                    recommendationMapper.insert(rec);
                } catch (DataIntegrityViolationException e) {
                    log.debug("推荐已存在,跳过 userId={} dedupKey={}", userId, rec.getDedupKey());
                }
            }
            existing = recommendationMapper.selectActiveByUserScene(userId, scene, now);
        }

        // 排序 + 截断
        existing = existing.stream()
                .sorted((a, b) -> {
                    int cmp = b.getScore().compareTo(a.getScore());
                    if (cmp != 0) return cmp;
                    return Long.compare(b.getId(), a.getId());
                })
                .limit(realLimit)
                .toList();

        List<RecommendationVO> items = existing.stream().map(this::toVO).toList();
        return new RecommendationListVO(items, StudyTimeUtils.toOffset(now));
    }

    private List<UserRecommendation> generateByScene(Long userId, String scene, Long contextId) {
        List<UserRecommendation> result = new ArrayList<>();
        switch (scene) {
            case "TODAY" -> result.addAll(recommendationEngine.generateTODAY(userId, LocalDate.now(studyClock)));
            case "CHAT" -> { if (contextId != null) result.addAll(recommendationEngine.generateCHAT(userId, contextId)); }
            case "TASK_COMPLETE" -> { if (contextId != null) result.addAll(recommendationEngine.generateTASK_COMPLETE(userId, contextId)); }
        }
        return result;
    }

    /**
     * 上下文归属校验。上下文不存在 → 404，存在但属于他人 → 10009。
     * 仅 MESSAGE / CONVERSATION / TASK 需要校验；NONE 直接跳过。
     */
    private void validateContextOwnership(Long userId, String contextType, Long contextId) {
        switch (contextType) {
            case "MESSAGE" -> {
                ChatMessage message = chatService.findMessage(contextId);
                if (message == null) {
                    throw new BizException(404, "上下文消息不存在");
                }
                if (!userId.equals(message.getUserId())) {
                    throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该上下文");
                }
            }
            case "CONVERSATION" -> chatService.findOwned(userId, contextId);
            case "TASK" -> {
                StudyTask task = studyTaskMapper.selectById(contextId);
                if (task == null) {
                    throw new BizException(404, "上下文任务不存在");
                }
                if (!userId.equals(task.getUserId())) {
                    throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该上下文");
                }
            }
            default -> { }
        }
    }

    /**
     * 接受推荐。双重幂等: @Idempotent (HTTP 层) + accepted_task_id (Service 层)。
     */
    @Transactional
    public AcceptRecommendationResult accept(Long userId, Long recId) {
        UserRecommendation rec = recommendationMapper.selectById(recId);
        if (rec == null) throw new BizException(404, "推荐不存在");
        if (!rec.getUserId().equals(userId))
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "推荐不属于当前用户");

        if ("DISMISSED".equals(rec.getStatus()) || "EXPIRED".equals(rec.getStatus()))
            throw new BizException(409, "该推荐已失效");

        if (!"CREATE_TASK".equals(rec.getActionType()))
            throw new BizException(409, "该推荐不能直接接受");

        // 已接受 → 返回原任务 (不同幂等 Key 重复接受也不重复建)
        if ("ACCEPTED".equals(rec.getStatus()) && rec.getAcceptedTaskId() != null) {
            StudyTask existingTask = studyTaskMapper.selectById(rec.getAcceptedTaskId());
            if (existingTask != null) {
                return new AcceptRecommendationResult(toVO(rec), StudyPlanService.toTaskVO(existingTask, LocalDateTime.now(studyClock)));
            }
        }

        // 创建任务
        StudyTask task = new StudyTask();
        task.setPlanId(studyPlanService.ensureTodayPlan(userId).getId());
        task.setUserId(userId);
        task.setPlanDate(LocalDate.now(studyClock));
        task.setTitle(rec.getTitle());
        task.setSubject(rec.getSubject() != null ? rec.getSubject() : "综合");
        task.setKnowledgePoint(rec.getKnowledgePoint());
        task.setEstimatedMinutes(rec.getEstimatedMinutes() != null ? rec.getEstimatedMinutes() : 20);
        task.setDueAt(null);
        task.setStatus("TODO");
        task.setPriority(90);
        task.setVersion(0);
        task.setCreateTime(LocalDateTime.now(studyClock));
        task.setUpdateTime(LocalDateTime.now(studyClock));
        studyTaskMapper.insert(task);

        // 更新推荐
        recommendationMapper.updateAccepted(recId, userId, task.getId());

        // 重查推荐获取最新状态
        UserRecommendation accepted = recommendationMapper.selectById(recId);
        return new AcceptRecommendationResult(toVO(accepted), StudyPlanService.toTaskVO(task, LocalDateTime.now(studyClock)));
    }

    /**
     * 忽略推荐。已忽略重复调用仍返回成功。
     */
    @Transactional
    public DismissRecommendationResult dismiss(Long userId, Long recId) {
        UserRecommendation rec = recommendationMapper.selectById(recId);
        if (rec == null) throw new BizException(404, "推荐不存在");
        if (!rec.getUserId().equals(userId))
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "推荐不属于当前用户");

        recommendationMapper.updateDismissed(recId, userId);

        return new DismissRecommendationResult(recId, "DISMISSED");
    }

    // ===== VO 转换 =====

    public RecommendationVO toVO(UserRecommendation r) {
        return new RecommendationVO(r.getId(), r.getScene(), r.getType(), r.getTitle(),
                r.getReason(), r.getActionType(), r.getActionLabel(), r.getActionPath(),
                r.getScore(), r.getEstimatedMinutes(), r.getSubject(), r.getKnowledgePoint(),
                r.getStatus(), StudyTimeUtils.toOffset(r.getCreateTime()), StudyTimeUtils.toOffset(r.getExpiresAt()));
    }
}
