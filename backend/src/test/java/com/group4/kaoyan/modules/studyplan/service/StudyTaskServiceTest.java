package com.group4.kaoyan.modules.studyplan.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.recommendation.engine.RecommendationEngine;
import com.group4.kaoyan.modules.recommendation.mapper.RecommendationMapper;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskUpdateRequest;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskWriteRequest;
import com.group4.kaoyan.modules.studyplan.entity.StudyPlan;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 学习任务 Service 单元测试。核心覆盖乐观锁、归属校验、状态机。
 *
 * <p>注意:RecommendationEngine 用真实实例(注入 mock 的 StudyTaskMapper),
 * 而不是 mock 具体类 —— 这样在 JDK 24 下也能稳定运行。engine 的触发与否
 * 通过它内部调用的 engineTaskMapper 来间接验证。</p>
 */
@DisplayName("学习任务 Service")
class StudyTaskServiceTest {

    private StudyTaskMapper studyTaskMapper;
    private StudyTaskMapper engineTaskMapper;
    private RecommendationMapper recommendationMapper;
    private RecommendationEngine recommendationEngine;
    private RealtimeDeliveryPort delivery;
    private StudyPlanService studyPlanService;
    private ChatService chatService;
    private UserProfileMapper userProfileMapper;
    private Clock clock;
    private StudyTaskService service;

    private static final Long USER_ID = 10001L;
    private static final Long OTHER_USER = 20002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);

    @BeforeEach
    void setUp() {
        studyTaskMapper = mock(StudyTaskMapper.class);
        engineTaskMapper = mock(StudyTaskMapper.class);
        recommendationMapper = mock(RecommendationMapper.class);
        delivery = mock(RealtimeDeliveryPort.class);
        studyPlanService = mock(StudyPlanService.class);
        chatService = mock(ChatService.class);
        userProfileMapper = mock(UserProfileMapper.class);
        clock = Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
        // engine 用真实实例,依赖被 mock
        recommendationEngine = new RecommendationEngine(
                engineTaskMapper, chatService, userProfileMapper, clock);
        // engine 内部查询默认返回空,避免 NPE
        when(engineTaskMapper.selectById(anyLong())).thenReturn(null);
        when(engineTaskMapper.selectByPlanId(anyLong())).thenReturn(Collections.emptyList());
        when(engineTaskMapper.selectByUserAndDate(anyLong(), any())).thenReturn(Collections.emptyList());

        service = new StudyTaskService(
                studyTaskMapper, recommendationMapper, recommendationEngine,
                delivery, studyPlanService, clock);
    }

    @Test
    @DisplayName("任务不存在 → 404")
    void updateStatusReturns404WhenTaskMissing() {
        when(studyTaskMapper.selectById(99L)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.updateStatus(USER_ID, 99L, new StudyTaskUpdateRequest("COMPLETED", 0)));
        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("他人任务 → 10009 无权访问")
    void updateStatusRejectsOtherUserTask() {
        StudyTask task = task(1L, OTHER_USER, "TODO", 0);
        when(studyTaskMapper.selectById(1L)).thenReturn(task);

        BizException e = assertThrows(BizException.class,
                () -> service.updateStatus(USER_ID, 1L, new StudyTaskUpdateRequest("COMPLETED", 0)));
        assertEquals(ErrorCode.NO_DATA_ACCESS, e.getCode());
        assertTrue(e.getMessage().contains("无权访问"));
    }

    @Test
    @DisplayName("版本冲突:expectedVersion 落后 → 409 任务已被修改")
    void updateStatusReturns409WhenVersionMismatch() {
        StudyTask task = task(1L, USER_ID, "TODO", 0);
        StudyTask fresh = task(1L, USER_ID, "IN_PROGRESS", 1); // 别人已改过,version 变 1
        when(studyTaskMapper.selectById(1L)).thenReturn(task).thenReturn(fresh);
        when(studyTaskMapper.updateWithVersion(task, 0)).thenReturn(0); // 0 行影响

        BizException e = assertThrows(BizException.class,
                () -> service.updateStatus(USER_ID, 1L, new StudyTaskUpdateRequest("COMPLETED", 0)));
        assertEquals(409, e.getCode());
        assertTrue(e.getMessage().contains("已被修改"));
    }

    @Test
    @DisplayName("TODO→COMPLETED 成功:version+1,返回最新 VO,并触发推荐生成")
    void updateStatusCompletesSuccessfully() {
        StudyTask task = task(1L, USER_ID, "TODO", 0);
        StudyTask updated = task(1L, USER_ID, "COMPLETED", 1);
        when(studyTaskMapper.selectById(1L)).thenReturn(task).thenReturn(updated);
        when(studyTaskMapper.updateWithVersion(task, 0)).thenReturn(1);

        StudyTaskVO vo = service.updateStatus(USER_ID, 1L, new StudyTaskUpdateRequest("COMPLETED", 0));

        assertEquals("COMPLETED", vo.status());
        assertEquals(1, vo.version());
        // COMPLETED 后应触发推荐生成:engine 会查 source task
        verify(engineTaskMapper, times(1)).selectById(1L);
    }

    @Test
    @DisplayName("非 COMPLETED 状态变更不触发推荐生成")
    void updateStatusToInProgressDoesNotTriggerRecommendation() {
        StudyTask task = task(1L, USER_ID, "TODO", 0);
        StudyTask updated = task(1L, USER_ID, "IN_PROGRESS", 1);
        when(studyTaskMapper.selectById(1L)).thenReturn(task).thenReturn(updated);
        when(studyTaskMapper.updateWithVersion(task, 0)).thenReturn(1);

        service.updateStatus(USER_ID, 1L, new StudyTaskUpdateRequest("IN_PROGRESS", 0));

        verify(engineTaskMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("COMPLETED→TODO 重新打开:version+1,不触发推荐生成")
    void reopenCompletedTaskDoesNotTriggerRecommendation() {
        StudyTask task = task(1L, USER_ID, "COMPLETED", 3);
        StudyTask updated = task(1L, USER_ID, "TODO", 4);
        when(studyTaskMapper.selectById(1L)).thenReturn(task).thenReturn(updated);
        when(studyTaskMapper.updateWithVersion(task, 3)).thenReturn(1);

        StudyTaskVO vo = service.updateStatus(USER_ID, 1L, new StudyTaskUpdateRequest("TODO", 3));

        assertEquals("TODO", vo.status());
        assertEquals(4, vo.version());
        verify(engineTaskMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("非法状态抛 IllegalArgumentException")
    void updateStatusRejectsInvalidStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTaskUpdateRequest("OVERDUE", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTaskUpdateRequest("CANCELLED", 0));
    }

    @Test
    @DisplayName("手工新增任务：归属今日计划、默认 TODO、返回生成 id")
    void createManualTask() {
        StudyPlan plan = new StudyPlan();
        plan.setId(100L);
        when(studyPlanService.ensureTodayPlan(USER_ID)).thenReturn(plan);
        doAnswer(invocation -> {
            StudyTask inserted = invocation.getArgument(0);
            inserted.setId(700L);
            when(studyTaskMapper.selectById(700L)).thenReturn(inserted);
            return 1;
        }).when(studyTaskMapper).insert(any(StudyTask.class));

        StudyTaskVO task = service.create(USER_ID, new StudyTaskWriteRequest(
                "复习极限", "数学", "极限", 30, NOW.plusHours(8), 80, null));

        assertEquals(700L, task.id());
        assertEquals("TODO", task.status());
        ArgumentCaptor<StudyTask> captor = ArgumentCaptor.forClass(StudyTask.class);
        verify(studyTaskMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getPlanId());
        assertEquals("复习极限", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("编辑任务：内容字段走独立乐观锁 UPDATE")
    void updateTaskDetails() {
        StudyTask existing = task(1L, USER_ID, "TODO", 2);
        StudyTask saved = task(1L, USER_ID, "TODO", 3);
        saved.setTitle("新的标题");
        when(studyTaskMapper.selectById(1L)).thenReturn(existing).thenReturn(saved);
        when(studyTaskMapper.updateDetailsWithVersion(any(StudyTask.class), eq(2))).thenReturn(1);

        StudyTaskVO result = service.updateDetails(USER_ID, 1L, new StudyTaskWriteRequest(
                "新的标题", "数学", "极限", 45, NOW.plusHours(6), 90, 2));

        assertEquals("新的标题", result.title());
        assertEquals(3, result.version());
    }

    @Test
    @DisplayName("删除任务：版本匹配后逻辑删除")
    void deleteTask() {
        StudyTask existing = task(1L, USER_ID, "TODO", 2);
        when(studyTaskMapper.selectById(1L)).thenReturn(existing);
        when(studyTaskMapper.logicDelete(1L, USER_ID, 2)).thenReturn(1);

        service.delete(USER_ID, 1L, 2);

        verify(studyTaskMapper).logicDelete(1L, USER_ID, 2);
    }

    // ===== helper =====

    private StudyTask task(Long id, Long userId, String status, int version) {
        StudyTask t = new StudyTask();
        t.setId(id);
        t.setUserId(userId);
        t.setPlanId(100L);
        t.setPlanDate(LocalDate.of(2026, 11, 26));
        t.setTitle("任务" + id);
        t.setSubject("数学");
        t.setStatus(status);
        t.setPriority(50);
        t.setVersion(version);
        t.setDeleted(0);
        t.setDueAt(NOW.plusHours(12));
        return t;
    }
}
