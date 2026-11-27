package com.group4.kaoyan.modules.studyplan.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.StudyTimeUtils;
import com.group4.kaoyan.modules.studyplan.dto.StudyPlanVO;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
import com.group4.kaoyan.modules.studyplan.dto.TodayStudySummary;
import com.group4.kaoyan.modules.studyplan.entity.StudyPlan;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyPlanMapper;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.modules.user.entity.UserProfile;
import com.group4.kaoyan.modules.user.entity.UserStudyGoal;
import com.group4.kaoyan.modules.user.mapper.UserStudyGoalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 学习计划 Service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanMapper studyPlanMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final UserStudyGoalMapper userStudyGoalMapper;
    private final Clock studyClock;

    /**
     * 获取今日学习计划。没有计划时返回空结构,不返回 404。
     */
    @Transactional(readOnly = true)
    public StudyPlanVO today(Long userId) {
        LocalDate today = LocalDate.now(studyClock);

        // 1. 有效备考目标
        UserStudyGoal goal = findActiveGoal(userId);
        LocalDate examDate = goal != null ? goal.getExamDate() : null;
        Integer daysUntilExam = null;
        if (examDate != null) {
            daysUntilExam = (int) ChronoUnit.DAYS.between(LocalDate.now(studyClock), examDate);
            if (daysUntilExam < 0) daysUntilExam = 0;
        }

        // 2. 今日计划
        StudyPlan plan = studyPlanMapper.selectByUserAndDate(userId, today);
        if (plan == null) {
            return new StudyPlanVO(0L, today, "", examDate, daysUntilExam,
                    TodayStudySummary.empty(), Collections.emptyList());
        }

        // 3. 任务
        List<StudyTask> tasks = studyTaskMapper.selectByPlanId(plan.getId());
        List<StudyTaskVO> taskVOs = tasks.stream()
                .map(t -> toTaskVO(t, LocalDateTime.now(studyClock)))
                .toList();

        // 4. Summary
        TodayStudySummary summary = computeSummary(tasks);

        return new StudyPlanVO(plan.getId(), plan.getPlanDate(), plan.getTitle(),
                examDate, daysUntilExam, summary, taskVOs);
    }

    /**
     * 生成今日学习计划。已有则直接返回,无备考目标则抛 400。
     */
    @Transactional
    public StudyPlanVO generate(Long userId) {
        LocalDate today = LocalDate.now(studyClock);

        // 已有计划 → 直接返回
        StudyPlan existing = studyPlanMapper.selectByUserAndDate(userId, today);
        if (existing != null) {
            return today(userId);
        }

        // 必须有有效备考目标
        UserStudyGoal goal = findActiveGoal(userId);
        if (goal == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先填写备考目标");
        }

        // 创建计划
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setPlanDate(today);
        plan.setTitle(buildTitle(goal));
        plan.setStatus("ACTIVE");
        plan.setVersion(0);
        plan.setCreateTime(LocalDateTime.now(studyClock));
        plan.setUpdateTime(LocalDateTime.now(studyClock));

        try {
            studyPlanMapper.insert(plan);
        } catch (DataIntegrityViolationException e) {
            // (user_id, plan_date) UNIQUE 冲突 → 并发重复提交,重查返回
            log.info("生成计划时发现重复,重查已有计划 userId={}", userId);
            return today(userId);
        }

        // 生成 1~3 个任务
        generateTasks(userId, plan, goal);

        return today(userId);
    }

    // ===== 内部方法 =====

    private UserStudyGoal findActiveGoal(Long userId) {
        List<UserStudyGoal> goals = userStudyGoalMapper.selectByUserId(userId, 1);
        return goals.isEmpty() ? null : goals.get(0);
    }

    private String buildTitle(UserStudyGoal goal) {
        if (goal.getTargetSchoolName() != null && !goal.getTargetSchoolName().isBlank()) {
            return goal.getTargetSchoolName() + (goal.getTargetMajor() != null
                    ? " " + goal.getTargetMajor() : "") + " 备考计划";
        }
        return "考研学习计划";
    }

    private void generateTasks(Long userId, StudyPlan plan, UserStudyGoal goal) {
        LocalDate today = plan.getPlanDate();
        LocalDateTime now = LocalDateTime.now(studyClock);
        LocalDateTime dayEnd = today.atTime(23, 59, 59);

        // 从目标单科分构造任务:有单科分 → 每个科目一个任务,无则 1 个通用任务
        List<String> subjects = extractSubjects(goal);
        int taskCount = Math.max(1, Math.min(3, subjects.size()));
        if (subjects.isEmpty()) {
            subjects = List.of("综合");
        }

        for (int i = 0; i < taskCount; i++) {
            String subject = subjects.get(i);
            StudyTask task = new StudyTask();
            task.setPlanId(plan.getId());
            task.setUserId(userId);
            task.setPlanDate(today);
            task.setTitle(subject + "：基础知识点复习");
            task.setSubject(subject);
            task.setEstimatedMinutes(20 + (i * 5)); // 20~30 min
            task.setDueAt(dayEnd);
            task.setStatus("TODO");
            task.setPriority(50 + i * 10); // 50, 60, 70
            task.setVersion(0);
            task.setCreateTime(now);
            task.setUpdateTime(now);
            studyTaskMapper.insert(task);
        }
    }

    private List<String> extractSubjects(UserStudyGoal goal) {
        // targetSubjectScores 存的是 JSON,此处简化处理
        List<String> subjects = new ArrayList<>();
        if (goal.getTargetSubjectScores() != null) {
            // 简单识别常见科目键
            String json = goal.getTargetSubjectScores().toString().toLowerCase();
            if (json.contains("english") || json.contains("英语")) subjects.add("英语");
            if (json.contains("math") || json.contains("数学")) subjects.add("数学");
            if (json.contains("politics") || json.contains("政治")) subjects.add("政治");
            if (json.contains("chinese") || json.contains("语文")) subjects.add("专业课");
        }
        return subjects;
    }

    public static TodayStudySummary computeSummary(List<StudyTask> tasks) {
        int completed = 0;
        int total = 0;
        int studyMinutes = 0;
        for (StudyTask t : tasks) {
            if ("CANCELLED".equals(t.getStatus())) continue;
            total++;
            if ("COMPLETED".equals(t.getStatus())) {
                completed++;
                studyMinutes += t.getEstimatedMinutes() != null ? t.getEstimatedMinutes() : 0;
            }
        }
        return new TodayStudySummary(completed, total, studyMinutes, 0);
    }

    public static StudyTaskVO toTaskVO(StudyTask t, LocalDateTime now) {
        boolean overdue = !"COMPLETED".equals(t.getStatus())
                && !"CANCELLED".equals(t.getStatus())
                && t.getDueAt() != null
                && t.getDueAt().isBefore(now);
        return new StudyTaskVO(t.getId(), t.getTitle(), t.getSubject(),
                t.getKnowledgePoint(), t.getEstimatedMinutes(),
                StudyTimeUtils.toOffset(t.getDueAt()),
                t.getStatus(), overdue, t.getPriority(), t.getVersion());
    }
}
