package com.group4.kaoyan.modules.studyplan.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.studyplan.dto.StudyPlanVO;
import com.group4.kaoyan.modules.studyplan.dto.TodayStudySummary;
import com.group4.kaoyan.modules.studyplan.entity.StudyPlan;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyPlanMapper;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.modules.user.entity.UserStudyGoal;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import com.group4.kaoyan.modules.user.mapper.UserStudyGoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import org.mockito.stubbing.Answer;

/**
 * 学习计划 Service 单元测试。纯 Mockito,不依赖 Spring/数据库。
 */
@DisplayName("学习计划 Service")
class StudyPlanServiceTest {

    private StudyPlanMapper studyPlanMapper;
    private StudyTaskMapper studyTaskMapper;
    private UserStudyGoalMapper userStudyGoalMapper;
    private UserProfileMapper userProfileMapper;
    private Clock clock;
    private StudyPlanService service;

    private static final Long USER_ID = 10001L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final LocalDateTime NOW = TODAY.atTime(10, 0, 0);

    @BeforeEach
    void setUp() {
        studyPlanMapper = mock(StudyPlanMapper.class);
        studyTaskMapper = mock(StudyTaskMapper.class);
        userStudyGoalMapper = mock(UserStudyGoalMapper.class);
        userProfileMapper = mock(UserProfileMapper.class);
        // 固定 Clock 到 2026-11-26 10:00 +08:00
        clock = Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
        service = new StudyPlanService(
                studyPlanMapper, studyTaskMapper, userStudyGoalMapper, userProfileMapper, clock);
    }

    @Test
    @DisplayName("无计划无目标:返回空结构 planId=0 tasks=[] summary全0 daysUntilExam=null")
    void todayReturnsEmptyStructureWhenNoPlan() {
        when(userStudyGoalMapper.selectByUserId(USER_ID, 1)).thenReturn(Collections.emptyList());
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(null);

        StudyPlanVO vo = service.today(USER_ID);

        assertEquals(0L, vo.planId());
        assertEquals(TODAY, vo.planDate());
        assertNull(vo.examDate());
        assertNull(vo.daysUntilExam());
        assertNotNull(vo.tasks());
        assertTrue(vo.tasks().isEmpty());
        TodayStudySummary s = vo.summary();
        assertEquals(0, s.completedTasks());
        assertEquals(0, s.totalTasks());
        assertEquals(0, s.studyMinutes());
    }

    @Test
    @DisplayName("无计划但有目标:daysUntilExam 正确计算")
    void todayComputesDaysUntilExam() {
        UserStudyGoal goal = new UserStudyGoal();
        goal.setExamDate(TODAY.plusDays(30));
        when(userStudyGoalMapper.selectByUserId(USER_ID, 1)).thenReturn(List.of(goal));
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(null);

        StudyPlanVO vo = service.today(USER_ID);

        assertEquals(30, vo.daysUntilExam());
        assertEquals(TODAY.plusDays(30), vo.examDate());
    }

    @Test
    @DisplayName("逾期计算:dueAt 早于 now 且未完成 → overdue=true;已完成 → overdue=false")
    void todayComputesOverdueCorrectly() {
        UserStudyGoal goal = new UserStudyGoal();
        goal.setExamDate(TODAY.plusDays(10));
        when(userStudyGoalMapper.selectByUserId(USER_ID, 1)).thenReturn(List.of(goal));

        StudyPlan plan = new StudyPlan();
        plan.setId(100L);
        plan.setUserId(USER_ID);
        plan.setPlanDate(TODAY);
        plan.setTitle("测试计划");
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(plan);

        StudyTask overdueTask = task(1L, "TODO", TODAY.minusDays(1).atTime(23, 59), 30, null);
        StudyTask completedTask = task(2L, "COMPLETED", TODAY.minusDays(1).atTime(23, 59), 40, NOW.minusHours(1));
        StudyTask inProgress = task(3L, "IN_PROGRESS", TODAY.plusDays(1).atTime(23, 59), 20, null);
        when(studyTaskMapper.selectByUserAndDate(USER_ID, TODAY))
                .thenReturn(List.of(overdueTask, completedTask, inProgress));

        StudyPlanVO vo = service.today(USER_ID);

        assertEquals(3, vo.tasks().size());
        assertTrue(vo.tasks().get(0).overdue(), "未完成且 dueAt 早于 now 应逾期");
        assertFalse(vo.tasks().get(1).overdue(), "已完成不应视为逾期");
        assertFalse(vo.tasks().get(2).overdue(), "dueAt 晚于 now 不应逾期");
    }

    @Test
    @DisplayName("summary:2 completed+1 in_progress+1 cancelled → completedTasks=2 totalTasks=3 studyMinutes=70")
    void todayComputesSummaryExcludingCancelled() {
        when(userStudyGoalMapper.selectByUserId(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        StudyPlan plan = new StudyPlan();
        plan.setId(200L);
        plan.setUserId(USER_ID);
        plan.setPlanDate(TODAY);
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(plan);

        StudyTask t1 = task(1L, "COMPLETED", null, 30, NOW);
        StudyTask t2 = task(2L, "COMPLETED", null, 40, NOW);
        StudyTask t3 = task(3L, "IN_PROGRESS", null, 25, null);
        StudyTask t4 = task(4L, "CANCELLED", null, 50, null);
        when(studyTaskMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(List.of(t1, t2, t3, t4));

        StudyPlanVO vo = service.today(USER_ID);

        TodayStudySummary s = vo.summary();
        assertEquals(2, s.completedTasks());
        assertEquals(3, s.totalTasks(), "CANCELLED 不计入 totalTasks");
        assertEquals(70, s.studyMinutes(), "studyMinutes 只计 COMPLETED");
    }

    @Test
    @DisplayName("generate:无备考目标 → 抛 10000 请先填写备考目标")
    void generateThrowsWhenNoGoal() {
        when(userStudyGoalMapper.selectByUserId(USER_ID, 1)).thenReturn(Collections.emptyList());
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.generate(USER_ID));
        assertEquals(ErrorCode.PARAM_INVALID, e.getCode());
        assertTrue(e.getMessage().contains("备考目标"));
    }

    @Test
    @DisplayName("generate:已有空计划 → 复用计划并补生成任务")
    void generateFillsExistingEmptyPlan() {
        UserStudyGoal goal = new UserStudyGoal();
        goal.setTargetSchoolName("测试大学");
        goal.setTargetMajor("计算机");
        goal.setExamDate(TODAY.plusDays(20));
        when(userStudyGoalMapper.selectByUserId(USER_ID, 1)).thenReturn(List.of(goal));

        StudyPlan existing = new StudyPlan();
        existing.setId(300L);
        existing.setUserId(USER_ID);
        existing.setPlanDate(TODAY);
        existing.setTitle("已有计划");
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(existing);
        when(studyTaskMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(Collections.emptyList());

        StudyPlanVO vo = service.generate(USER_ID);

        assertEquals(300L, vo.planId());
        verify(studyPlanMapper, never()).insert(any());
        verify(studyTaskMapper, atLeast(1)).insert(any());
    }

    @Test
    @DisplayName("generate:无计划有目标 → 创建计划+任务,返回完整结构")
    void generateCreatesPlanAndTasks() {
        UserStudyGoal goal = new UserStudyGoal();
        goal.setTargetSchoolName("目标大学");
        goal.setTargetMajor("软件工程");
        goal.setExamDate(TODAY.plusDays(100));
        goal.setTargetSubjectScores("{\"english\":70,\"math\":110}");
        when(userStudyGoalMapper.selectByUserId(USER_ID, 1)).thenReturn(List.of(goal));
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(null);
        doAnswer((Answer<Void>) invocation -> {
            StudyPlan inserted = invocation.getArgument(0);
            inserted.setId(400L);
            return null;
        }).when(studyPlanMapper).insert(any(StudyPlan.class));

        StudyPlan created = new StudyPlan();
        created.setId(400L);
        created.setUserId(USER_ID);
        created.setPlanDate(TODAY);
        created.setTitle("目标大学 软件工程 备考计划");
        when(studyPlanMapper.selectByUserAndDate(USER_ID, TODAY))
                .thenReturn(null)
                .thenReturn(created);
        when(studyTaskMapper.selectByUserAndDate(USER_ID, TODAY)).thenReturn(Collections.emptyList());

        StudyPlanVO vo = service.generate(USER_ID);

        assertEquals(400L, vo.planId());
        verify(studyPlanMapper, times(1)).insert(any(StudyPlan.class));
        // 应该插入了至少 1 个任务(从 english/math 识别出 2 科)
        verify(studyTaskMapper, atLeast(1)).insert(any(StudyTask.class));
    }

    // ===== helper =====

    private StudyTask task(Long id, String status, LocalDateTime dueAt, Integer estimated, LocalDateTime completedAt) {
        StudyTask t = new StudyTask();
        t.setId(id);
        t.setUserId(USER_ID);
        t.setPlanId(100L);
        t.setPlanDate(TODAY);
        t.setTitle("任务" + id);
        t.setSubject("数学");
        t.setStatus(status);
        t.setDueAt(dueAt);
        t.setEstimatedMinutes(estimated);
        t.setPriority(50);
        t.setVersion(0);
        t.setCompletedAt(completedAt);
        t.setDeleted(0);
        return t;
    }
}
