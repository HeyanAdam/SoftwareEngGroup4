package com.group4.kaoyan.modules.recommendation.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.modules.user.entity.UserProfile;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则式推荐引擎。不调 LLM，规则失败返回空数组。
 *
 * <p>三个场景的规则见《后端 A》文档 §13.2：
 * <ul>
 *   <li>TODAY：逾期未完成 &gt; 高优先级 TODO &gt; 薄弱知识点 &gt; 即将截止</li>
 *   <li>CHAT：校验消息归属 → 匹配科目关键词 → 同科未完成任务 → 无任务时知识点复习</li>
 *   <li>TASK_COMPLETE：同计划下一个未完成 &gt; 同科/薄弱点任务 &gt; 最近聊天相关知识点</li>
 * </ul></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 科目关键词 → 规范科目名，按顺序匹配，长词优先。 */
    private static final Map<String, String> SUBJECT_KEYWORDS = Map.ofEntries(
            Map.entry("高数", "数学"), Map.entry("高等数学", "数学"), Map.entry("线性代数", "数学"),
            Map.entry("线代", "数学"), Map.entry("概率论", "数学"), Map.entry("数学", "数学"),
            Map.entry("英语", "英语"), Map.entry("阅读理解", "英语"), Map.entry("作文", "英语"),
            Map.entry("政治", "政治"), Map.entry("马原", "政治"), Map.entry("毛中特", "政治"),
            Map.entry("数据结构", "专业课"), Map.entry("操作系统", "专业课"),
            Map.entry("计算机网络", "专业课"), Map.entry("组成原理", "专业课"),
            Map.entry("408", "专业课"), Map.entry("专业课", "专业课"));

    private final StudyTaskMapper studyTaskMapper;
    private final ChatService chatService;
    private final UserProfileMapper userProfileMapper;
    private final Clock studyClock;

    /**
     * 今日场景推荐：逾期未完成 &gt; 高优先级 TODO &gt; 薄弱知识点 &gt; 即将截止。
     */
    public List<UserRecommendation> generateTODAY(Long userId, LocalDate today) {
        LocalDateTime now = LocalDateTime.now(studyClock);
        List<StudyTask> tasks = studyTaskMapper.selectByUserAndDate(userId, today);
        List<UserRecommendation> result = new ArrayList<>();

        // 规则 1：逾期未完成
        for (StudyTask t : tasks) {
            if (isOverdue(t, now)) {
                result.add(buildRec(userId, "TODAY", "NONE", null, "STUDY_TASK",
                        t.getTitle(), "该任务已逾期,建议尽快完成",
                        "CREATE_TASK", "加入今日计划", null,
                        new BigDecimal("0.95"), t.getEstimatedMinutes(),
                        t.getSubject(), t.getKnowledgePoint(),
                        "TODAY:" + userId + ":OVERDUE:" + t.getId()));
            }
        }

        // 规则 2：高优先级 TODO（priority >= 70）
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
                    t.getSubject(), t.getKnowledgePoint(),
                    "TODAY:" + userId + ":HIGH:" + t.getId()));
        }

        // 规则 3：薄弱知识点（来自 user_profile.weak_points_json）
        for (String weak : extractWeakPoints(userId)) {
            if (result.size() >= 3) break;
            result.add(buildRec(userId, "TODAY", "NONE", null, "KNOWLEDGE",
                    "薄弱知识点复习: " + weak, "画像标记的薄弱点,建议优先巩固",
                    "CREATE_TASK", "开始复习", null,
                    new BigDecimal("0.70"), 25, null, weak,
                    "TODAY:" + userId + ":WEAK:" + weak));
        }

        // 规则 4：即将截止（2 小时内到期且未完成）
        List<StudyTask> dueSoon = tasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) && !"CANCELLED".equals(t.getStatus()))
                .filter(t -> t.getDueAt() != null
                        && t.getDueAt().isAfter(now)
                        && t.getDueAt().isBefore(now.plusHours(2)))
                .filter(t -> !isOverdue(t, now))
                .limit(Math.max(0, 3 - result.size()))
                .toList();
        for (StudyTask t : dueSoon) {
            result.add(buildRec(userId, "TODAY", "NONE", null, "STUDY_TASK",
                    "即将截止: " + t.getTitle(), "2 小时内到期,建议先完成",
                    "CREATE_TASK", "立即处理", null,
                    new BigDecimal("0.65"), t.getEstimatedMinutes(),
                    t.getSubject(), t.getKnowledgePoint(),
                    "TODAY:" + userId + ":DUE:" + t.getId()));
        }

        return result.stream().limit(3).toList();
    }

    /**
     * 聊天场景推荐：校验消息归属 → 匹配科目关键词 → 同科未完成任务 → 无任务时知识点复习。
     */
    public List<UserRecommendation> generateCHAT(Long userId, Long messageId) {
        List<UserRecommendation> result = new ArrayList<>();
        ChatMessage message = chatService.findMessage(messageId);
        if (message == null || !userId.equals(message.getUserId())) {
            // 消息不存在或不属于当前用户：规则失败，返回空数组
            return result;
        }

        // 助手消息 → 取它回答的那个用户问题；用户消息 → 取自身内容
        String question = resolveQuestion(message);
        if (question == null || question.isBlank()) {
            return result;
        }

        String subject = matchSubject(question);
        if (subject == null) {
            return result;
        }

        // 同科目未完成任务
        List<StudyTask> tasks = studyTaskMapper.selectByUserAndDate(userId, LocalDate.now(studyClock));
        List<StudyTask> sameSubject = tasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) && !"CANCELLED".equals(t.getStatus()))
                .filter(t -> subject.equals(t.getSubject()))
                .limit(2)
                .toList();
        for (StudyTask t : sameSubject) {
            result.add(buildRec(userId, "CHAT", "MESSAGE", messageId, "STUDY_TASK",
                    t.getTitle(), "刚才聊到" + subject + ",这道任务正好可以接着做",
                    "CREATE_TASK", "加入任务", null,
                    new BigDecimal("0.85"), t.getEstimatedMinutes(),
                    t.getSubject(), t.getKnowledgePoint(),
                    "CHAT:" + userId + ":" + messageId + ":SUBJECT:" + t.getId()));
        }

        // 没有同科任务 → 推荐该科目的知识点复习
        if (result.isEmpty()) {
            String point = extractKnowledgePoint(question);
            result.add(buildRec(userId, "CHAT", "MESSAGE", messageId, "KNOWLEDGE",
                    subject + "知识点复习: " + point, "围绕刚才的提问做一次针对性巩固",
                    "CREATE_TASK", "开始复习", null,
                    new BigDecimal("0.70"), 20, subject, point,
                    "CHAT:" + userId + ":" + messageId + ":REVIEW"));
        }

        return result.stream().limit(3).toList();
    }

    /**
     * 任务完成场景推荐：同计划下一未完成 &gt; 同科/薄弱点任务 &gt; 最近聊天相关知识点。
     */
    public List<UserRecommendation> generateTASK_COMPLETE(Long userId, Long taskId) {
        List<UserRecommendation> result = new ArrayList<>();
        StudyTask source = studyTaskMapper.selectById(taskId);
        if (source == null) return result;

        // 规则 1：同计划下一个未完成任务
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

        // 规则 2：同科未完成任务
        List<StudyTask> sameSubject = studyTaskMapper.selectByUserAndDate(userId, source.getPlanDate()).stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) && !"CANCELLED".equals(t.getStatus()))
                .filter(t -> source.getSubject() != null && source.getSubject().equals(t.getSubject()))
                .filter(t -> !t.getId().equals(taskId))
                .filter(t -> !siblings.contains(t))
                .limit(Math.max(0, 3 - result.size()))
                .toList();
        for (StudyTask t : sameSubject) {
            result.add(buildRec(userId, "TASK_COMPLETE", "TASK", taskId, "STUDY_TASK",
                    t.getTitle(), "同科任务,趁感觉好时继续",
                    "CREATE_TASK", "加入任务", null,
                    new BigDecimal("0.75"), t.getEstimatedMinutes(),
                    t.getSubject(), t.getKnowledgePoint(),
                    "TASK_COMPLETE:" + userId + ":" + taskId + ":SUBJECT:" + t.getId()));
        }

        // 规则 3a：同为薄弱点
        for (String weak : extractWeakPoints(userId)) {
            if (result.size() >= 3) break;
            result.add(buildRec(userId, "TASK_COMPLETE", "TASK", taskId, "KNOWLEDGE",
                    "薄弱知识点巩固: " + weak, "刚完成的任务告一段落,顺手补一下薄弱点",
                    "CREATE_TASK", "开始巩固", null,
                    new BigDecimal("0.70"), 25, source.getSubject(), weak,
                    "TASK_COMPLETE:" + userId + ":" + taskId + ":WEAK:" + weak));
        }

        // 规则 3b：最近聊天中的相关知识点
        if (result.size() < 3) {
            ChatMessage latest = chatService.findLatestUserMessage(userId);
            if (latest != null && latest.getContent() != null) {
                String subject = matchSubject(latest.getContent());
                if (subject != null) {
                    String point = extractKnowledgePoint(latest.getContent());
                    result.add(buildRec(userId, "TASK_COMPLETE", "TASK", taskId, "KNOWLEDGE",
                            subject + "回顾: " + point, "结合你最近问过的问题做一次回顾",
                            "CREATE_TASK", "回顾一下", null,
                            new BigDecimal("0.65"), 20, subject, point,
                            "TASK_COMPLETE:" + userId + ":" + taskId + ":CHAT:" + latest.getId()));
                }
            }
        }

        return result.stream().limit(3).toList();
    }

    // ===== 辅助 =====

    private boolean isOverdue(StudyTask t, LocalDateTime now) {
        return !"COMPLETED".equals(t.getStatus())
                && !"CANCELLED".equals(t.getStatus())
                && t.getDueAt() != null
                && t.getDueAt().isBefore(now);
    }

    private String resolveQuestion(ChatMessage message) {
        if (!"assistant".equals(message.getRole())) {
            return message.getContent();
        }
        if (message.getReplyToId() == null) {
            return message.getContent();
        }
        ChatMessage question = chatService.findMessage(message.getReplyToId());
        return question == null ? message.getContent() : question.getContent();
    }

    /** 从文本中匹配科目关键词，命中多个时取最先出现的。 */
    private String matchSubject(String text) {
        if (text == null) return null;
        String subject = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Map.Entry<String, String> e : SUBJECT_KEYWORDS.entrySet()) {
            int idx = text.indexOf(e.getKey());
            if (idx >= 0 && idx < bestIndex) {
                bestIndex = idx;
                subject = e.getValue();
            }
        }
        return subject;
    }

    /** 取问题中最长的一个科目关键词作为知识点名，兜底用「基础知识点」。 */
    private String extractKnowledgePoint(String text) {
        if (text == null) return "基础知识点";
        String best = null;
        for (String keyword : SUBJECT_KEYWORDS.keySet()) {
            if (text.contains(keyword) && (best == null || keyword.length() > best.length())) {
                best = keyword;
            }
        }
        return best == null ? "基础知识点" : best;
    }

    /** 读取画像薄弱点，兼容 List / Map / JSON 字符串三种存储形态。 */
    private List<String> extractWeakPoints(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null || profile.getWeakPointsJson() == null) {
            return List.of();
        }
        Object raw = profile.getWeakPointsJson();
        Set<String> points = new LinkedHashSet<>();

        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String v = itemToString(item);
                if (v != null) points.add(v);
            }
        } else if (raw instanceof Map<?, ?> map) {
            Object nested = map.get("points");
            if (nested instanceof List<?> list) {
                for (Object item : list) {
                    String v = itemToString(item);
                    if (v != null) points.add(v);
                }
            }
        } else {
            String text = String.valueOf(raw).trim();
            if (text.startsWith("[")) {
                try {
                    List<String> parsed = JSON.readValue(text, new TypeReference<List<String>>() { });
                    parsed.stream().filter(p -> p != null && !p.isBlank()).forEach(points::add);
                } catch (Exception e) {
                    log.debug("薄弱点 JSON 解析失败，忽略 userId={}", userId);
                }
            } else if (!text.isEmpty()) {
                points.add(text);
            }
        }
        return points.stream().filter(p -> !p.isBlank()).limit(3).toList();
    }

    private String itemToString(Object item) {
        if (item == null) return null;
        if (item instanceof Map<?, ?> m) {
            for (String key : List.of("knowledgePoint", "name", "point", "name_")) {
                Object v = m.get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v);
                }
            }
            return null;
        }
        String s = String.valueOf(item);
        return s.isBlank() ? null : s;
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
