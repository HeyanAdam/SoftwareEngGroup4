package com.group4.kaoyan.modules.recommendation.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.recommendation.dto.*;
import com.group4.kaoyan.modules.recommendation.engine.RecommendationEngine;
import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import com.group4.kaoyan.modules.recommendation.mapper.RecommendationMapper;
import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import com.group4.kaoyan.modules.studyplan.entity.StudyPlan;
import com.group4.kaoyan.modules.studyplan.mapper.StudyTaskMapper;
import com.group4.kaoyan.modules.studyplan.service.StudyPlanService;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 推荐 Service 单元测试。覆盖去重、排序、幂等接受、失效拒绝、归属校验。
 *
 * <p>RecommendationEngine 用真实实例(注入 mock 的 StudyTaskMapper),
 * 避免在 JDK 24 下 mock 具体类失败。</p>
 */
@DisplayName("推荐 Service")
class RecommendationServiceTest {

    private RecommendationMapper recommendationMapper;
    private StudyTaskMapper serviceTaskMapper;     // accept 时建任务用
    private StudyTaskMapper engineTaskMapper;      // engine 内部查询用
    private RecommendationEngine recommendationEngine;
    private StudyPlanService studyPlanService;
    private ChatService chatService;
    private UserProfileMapper userProfileMapper;
    private Clock clock;
    private RecommendationService service;

    private static final Long USER_ID = 10001L;
    private static final Long OTHER_USER = 20002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @BeforeEach
    void setUp() {
        recommendationMapper = mock(RecommendationMapper.class);
        serviceTaskMapper = mock(StudyTaskMapper.class);
        engineTaskMapper = mock(StudyTaskMapper.class);
        clock = Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
        studyPlanService = mock(StudyPlanService.class);
        chatService = mock(ChatService.class);
        userProfileMapper = mock(UserProfileMapper.class);
        StudyPlan plan = new StudyPlan();
        plan.setId(900L);
        when(studyPlanService.ensureTodayPlan(USER_ID)).thenReturn(plan);
        recommendationEngine = new RecommendationEngine(
                engineTaskMapper, chatService, userProfileMapper, clock);
        service = new RecommendationService(
                recommendationMapper, recommendationEngine, serviceTaskMapper,
                studyPlanService, chatService, clock);

        // engine 默认查询空
        when(engineTaskMapper.selectById(anyLong())).thenReturn(null);
        when(engineTaskMapper.selectByPlanId(anyLong())).thenReturn(Collections.emptyList());
        when(engineTaskMapper.selectByUserAndDate(anyLong(), any())).thenReturn(Collections.emptyList());
    }

    // ===== list 参数校验 =====

    @Test
    @DisplayName("list:非法 scene → 10000")
    void listRejectsInvalidScene() {
        BizException e = assertThrows(BizException.class,
                () -> service.list(USER_ID, "INVALID", "NONE", null, null));
        assertEquals(ErrorCode.PARAM_INVALID, e.getCode());
    }

    @Test
    @DisplayName("list:contextType 非 NONE 但 contextId 为 null → 10000")
    void listRejectsMissingContextId() {
        BizException e = assertThrows(BizException.class,
                () -> service.list(USER_ID, "TASK_COMPLETE", "TASK", null, null));
        assertEquals(ErrorCode.PARAM_INVALID, e.getCode());
    }

    @Test
    @DisplayName("list:TODAY 不传 contextType 时按 NONE 处理")
    void listAcceptsMissingContextTypeForToday() {
        when(recommendationMapper.selectActiveByUserScene(USER_ID, "TODAY", NOW))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(rec(1L, "ACTIVE", new BigDecimal("0.90"))));

        RecommendationListVO result = service.list(USER_ID, "TODAY", null, null, 3);

        assertEquals(1, result.items().size());
    }

    // ===== list 缓存与排序 =====

    @Test
    @DisplayName("list:有缓存 → 直接返回,不触发生成,score DESC + id DESC 排序")
    void listReturnsCachedSorted() {
        UserRecommendation r1 = rec(1L, "ACTIVE", new BigDecimal("0.80"));
        UserRecommendation r2 = rec(2L, "ACTIVE", new BigDecimal("0.95"));
        UserRecommendation r3 = rec(3L, "ACTIVE", new BigDecimal("0.95")); // 同分 id 大的在前
        when(recommendationMapper.selectActiveByUserScene(eq(USER_ID), eq("TODAY"), any()))
                .thenReturn(List.of(r1, r2, r3));

        RecommendationListVO vo = service.list(USER_ID, "TODAY", "NONE", null, null);

        assertEquals(3, vo.items().size());
        assertEquals(3L, vo.items().get(0).id());
        assertEquals(2L, vo.items().get(1).id());
        assertEquals(1L, vo.items().get(2).id());
        verify(engineTaskMapper, never()).selectByUserAndDate(anyLong(), any());
    }

    @Test
    @DisplayName("list:limit 截断")
    void listRespectsLimit() {
        UserRecommendation r1 = rec(1L, "ACTIVE", new BigDecimal("0.9"));
        UserRecommendation r2 = rec(2L, "ACTIVE", new BigDecimal("0.8"));
        UserRecommendation r3 = rec(3L, "ACTIVE", new BigDecimal("0.7"));
        UserRecommendation r4 = rec(4L, "ACTIVE", new BigDecimal("0.6"));
        when(recommendationMapper.selectActiveByUserScene(anyLong(), anyString(), any()))
                .thenReturn(List.of(r1, r2, r3, r4));

        RecommendationListVO vo = service.list(USER_ID, "TODAY", "NONE", null, 2);
        assertEquals(2, vo.items().size());
    }

    @Test
    @DisplayName("list:无缓存 → 触发 engine 生成并持久化,返回生成结果")
    void listGeneratesWhenCacheEmpty() {
        // 配置 engine 依赖:有一个逾期未完成任务 → generateTODAY 会产出 1 条推荐
        StudyTask overdueTask = new StudyTask();
        overdueTask.setId(50L);
        overdueTask.setUserId(USER_ID);
        overdueTask.setStatus("TODO");
        overdueTask.setPriority(50);
        overdueTask.setSubject("数学");
        overdueTask.setDueAt(TODAY.minusDays(1).atTime(23, 59)); // 早于 now → 逾期
        when(engineTaskMapper.selectByUserAndDate(eq(USER_ID), eq(TODAY))).thenReturn(List.of(overdueTask));

        UserRecommendation gen = rec(10L, "ACTIVE", new BigDecimal("0.95"));
        when(recommendationMapper.selectActiveByUserScene(anyLong(), anyString(), any()))
                .thenReturn(Collections.emptyList())   // 首次查无
                .thenReturn(List.of(gen));              // 生成后重查有

        RecommendationListVO vo = service.list(USER_ID, "TODAY", "NONE", null, null);

        assertEquals(1, vo.items().size());
        assertEquals(10L, vo.items().get(0).id());
        verify(recommendationMapper, atLeast(1)).insert(any(UserRecommendation.class));
    }

    @Test
    @DisplayName("list:生成时唯一键冲突 → 忽略重复插入,用已有数据返回")
    void listIgnoresDuplicateInsert() {
        StudyTask overdueTask = new StudyTask();
        overdueTask.setId(50L);
        overdueTask.setUserId(USER_ID);
        overdueTask.setStatus("TODO");
        overdueTask.setPriority(50);
        overdueTask.setSubject("数学");
        overdueTask.setDueAt(TODAY.minusDays(1).atTime(23, 59));
        when(engineTaskMapper.selectByUserAndDate(eq(USER_ID), eq(TODAY)))
                .thenReturn(List.of(overdueTask));
        when(recommendationMapper.insert(any(UserRecommendation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate dedup_key"));
        when(recommendationMapper.selectActiveByUserScene(anyLong(), anyString(), any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(rec(10L, "ACTIVE", new BigDecimal("0.95"))));

        RecommendationListVO vo = service.list(USER_ID, "TODAY", "NONE", null, null);

        assertEquals(1, vo.items().size(), "重复插入不应让整个查询失败");
        assertEquals(10L, vo.items().get(0).id());
    }

    // ===== list 上下文归属 =====

    @Test
    @DisplayName("list:CHAT 消息命中科目 → 生成同科任务推荐,dedupKey 带 SUBJECT")
    void listChatMatchesSubjectTask() {
        ChatMessage message = new ChatMessage();
        message.setId(88L);
        message.setUserId(USER_ID);
        message.setRole("user");
        message.setContent("高数极限怎么求?");
        when(chatService.findMessage(88L)).thenReturn(message);

        StudyTask mathTask = new StudyTask();
        mathTask.setId(99L);
        mathTask.setUserId(USER_ID);
        mathTask.setStatus("TODO");
        mathTask.setSubject("数学");
        mathTask.setTitle("数学基础复习");
        when(engineTaskMapper.selectByUserAndDate(eq(USER_ID), eq(TODAY)))
                .thenReturn(List.of(mathTask));
        when(recommendationMapper.selectActiveByUserScene(anyLong(), anyString(), any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(rec(20L, "ACTIVE", new BigDecimal("0.85"))));

        RecommendationListVO vo = service.list(USER_ID, "CHAT", "MESSAGE", 88L, null);

        assertEquals(1, vo.items().size());
        verify(recommendationMapper).insert(argThat(r ->
                r.getDedupKey().contains("SUBJECT:99") && Long.valueOf(88L).equals(r.getContextId())));
    }

    @Test
    @DisplayName("list:上下文消息不存在 → 404")
    void listRejectsMissingContextMessage() {
        when(chatService.findMessage(88L)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.list(USER_ID, "CHAT", "MESSAGE", 88L, null));
        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("list:他人上下文消息 → 10009")
    void listRejectsOtherUserContextMessage() {
        ChatMessage message = new ChatMessage();
        message.setId(88L);
        message.setUserId(OTHER_USER);
        message.setContent("高数极限怎么求?");
        when(chatService.findMessage(88L)).thenReturn(message);

        BizException e = assertThrows(BizException.class,
                () -> service.list(USER_ID, "CHAT", "MESSAGE", 88L, null));
        assertEquals(ErrorCode.NO_DATA_ACCESS, e.getCode());
    }

    @Test
    @DisplayName("list:他人上下文任务 → 10009")
    void listRejectsOtherUserContextTask() {
        StudyTask task = new StudyTask();
        task.setId(77L);
        task.setUserId(OTHER_USER);
        when(serviceTaskMapper.selectById(77L)).thenReturn(task);

        BizException e = assertThrows(BizException.class,
                () -> service.list(USER_ID, "TASK_COMPLETE", "TASK", 77L, null));
        assertEquals(ErrorCode.NO_DATA_ACCESS, e.getCode());
    }

    @Test
    @DisplayName("list:上下文任务不存在 → 404")
    void listRejectsMissingContextTask() {
        when(serviceTaskMapper.selectById(77L)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.list(USER_ID, "TASK_COMPLETE", "TASK", 77L, null));
        assertEquals(404, e.getCode());
    }

    // ===== accept =====

    @Test
    @DisplayName("accept:推荐不存在 → 404")
    void acceptReturns404WhenMissing() {
        when(recommendationMapper.selectById(99L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.accept(USER_ID, 99L));
        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("accept:他人推荐 → 10009")
    void acceptRejectsOtherUser() {
        UserRecommendation rec = rec(1L, "ACTIVE", new BigDecimal("0.9"));
        rec.setUserId(OTHER_USER);
        when(recommendationMapper.selectById(1L)).thenReturn(rec);
        BizException e = assertThrows(BizException.class, () -> service.accept(USER_ID, 1L));
        assertEquals(ErrorCode.NO_DATA_ACCESS, e.getCode());
    }

    @Test
    @DisplayName("accept:已 DISMISSED → 409 该推荐已失效")
    void acceptRejectsDismissed() {
        UserRecommendation rec = rec(1L, "DISMISSED", new BigDecimal("0.9"));
        when(recommendationMapper.selectById(1L)).thenReturn(rec);
        BizException e = assertThrows(BizException.class, () -> service.accept(USER_ID, 1L));
        assertEquals(409, e.getCode());
        assertTrue(e.getMessage().contains("失效"));
    }

    @Test
    @DisplayName("accept:非 CREATE_TASK → 409")
    void acceptRejectsNonCreateTask() {
        UserRecommendation rec = rec(1L, "ACTIVE", new BigDecimal("0.9"));
        rec.setActionType("OPEN_CHAT");
        when(recommendationMapper.selectById(1L)).thenReturn(rec);
        BizException e = assertThrows(BizException.class, () -> service.accept(USER_ID, 1L));
        assertEquals(409, e.getCode());
    }

    @Test
    @DisplayName("accept:已 ACCEPTED 且有 acceptedTaskId → 返回原任务,不重复建(不同幂等 Key 幂等)")
    void acceptIdempotentReturnsExistingTask() {
        UserRecommendation accepted = rec(1L, "ACCEPTED", new BigDecimal("0.9"));
        accepted.setAcceptedTaskId(500L);
        StudyTask existingTask = new StudyTask();
        existingTask.setId(500L);
        existingTask.setUserId(USER_ID);
        existingTask.setStatus("TODO");
        existingTask.setVersion(0);
        existingTask.setDeleted(0);
        when(recommendationMapper.selectById(1L)).thenReturn(accepted);
        when(serviceTaskMapper.selectById(500L)).thenReturn(existingTask);

        AcceptRecommendationResult result = service.accept(USER_ID, 1L);

        assertNotNull(result.task());
        assertEquals(500L, result.task().id());
        verify(serviceTaskMapper, never()).insert(any());
        verify(recommendationMapper, never()).updateAccepted(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("accept:正常 → 创建任务+更新推荐状态为 ACCEPTED")
    void acceptCreatesTask() {
        UserRecommendation rec = rec(1L, "ACTIVE", new BigDecimal("0.9"));
        rec.setTitle("复习高数极限");
        rec.setSubject("数学");
        rec.setEstimatedMinutes(30);
        UserRecommendation after = rec(1L, "ACCEPTED", new BigDecimal("0.9"));
        after.setAcceptedTaskId(600L);
        when(recommendationMapper.selectById(1L)).thenReturn(rec).thenReturn(after);
        when(recommendationMapper.updateAccepted(eq(1L), eq(USER_ID), anyLong())).thenReturn(1);

        AcceptRecommendationResult result = service.accept(USER_ID, 1L);

        assertEquals("ACCEPTED", result.recommendation().status());
        assertNotNull(result.task());
        verify(serviceTaskMapper, times(1)).insert(any(StudyTask.class));
    }

    // ===== dismiss =====

    @Test
    @DisplayName("dismiss:他人推荐 → 10009")
    void dismissRejectsOtherUser() {
        UserRecommendation rec = rec(1L, "ACTIVE", new BigDecimal("0.9"));
        rec.setUserId(OTHER_USER);
        when(recommendationMapper.selectById(1L)).thenReturn(rec);
        BizException e = assertThrows(BizException.class, () -> service.dismiss(USER_ID, 1L));
        assertEquals(ErrorCode.NO_DATA_ACCESS, e.getCode());
    }

    @Test
    @DisplayName("dismiss:成功 → 返回 DISMISSED")
    void dismissSuccess() {
        UserRecommendation rec = rec(1L, "ACTIVE", new BigDecimal("0.9"));
        when(recommendationMapper.selectById(1L)).thenReturn(rec);
        when(recommendationMapper.updateDismissed(1L, USER_ID)).thenReturn(1);

        DismissRecommendationResult result = service.dismiss(USER_ID, 1L);

        assertEquals(1L, result.recommendationId());
        assertEquals("DISMISSED", result.status());
    }

    @Test
    @DisplayName("dismiss:已忽略重复调用仍返回成功(幂等)")
    void dismissIdempotentWhenAlreadyDismissed() {
        UserRecommendation rec = rec(1L, "DISMISSED", new BigDecimal("0.9"));
        when(recommendationMapper.selectById(1L)).thenReturn(rec);
        when(recommendationMapper.updateDismissed(1L, USER_ID)).thenReturn(0); // 0 行影响

        DismissRecommendationResult result = service.dismiss(USER_ID, 1L);
        assertEquals("DISMISSED", result.status());
    }

    // ===== helper =====

    private UserRecommendation rec(Long id, String status, BigDecimal score) {
        UserRecommendation r = new UserRecommendation();
        r.setId(id);
        r.setUserId(USER_ID);
        r.setScene("TODAY");
        r.setContextType("NONE");
        r.setType("STUDY_TASK");
        r.setTitle("推荐" + id);
        r.setReason("测试理由");
        r.setActionType("CREATE_TASK");
        r.setActionLabel("加入任务");
        r.setScore(score);
        r.setSubject("数学");
        r.setStatus(status);
        r.setAcceptedTaskId(null);
        r.setDedupKey("dedup-" + id);
        r.setCreateTime(NOW);
        r.setExpiresAt(NOW.plusDays(1));
        return r;
    }
}
