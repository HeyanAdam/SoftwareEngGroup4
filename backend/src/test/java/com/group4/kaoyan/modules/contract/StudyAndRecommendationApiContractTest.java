package com.group4.kaoyan.modules.contract;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.group4.kaoyan.common.GlobalExceptionHandler;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.modules.recommendation.controller.RecommendationController;
import com.group4.kaoyan.modules.recommendation.dto.AcceptRecommendationResult;
import com.group4.kaoyan.modules.recommendation.dto.DismissRecommendationResult;
import com.group4.kaoyan.modules.recommendation.dto.RecommendationListVO;
import com.group4.kaoyan.modules.recommendation.dto.RecommendationVO;
import com.group4.kaoyan.modules.recommendation.service.RecommendationService;
import com.group4.kaoyan.modules.studyplan.controller.StudyPlanController;
import com.group4.kaoyan.modules.studyplan.controller.StudyTaskController;
import com.group4.kaoyan.modules.studyplan.dto.StudyPlanVO;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
import com.group4.kaoyan.modules.studyplan.dto.TodayStudySummary;
import com.group4.kaoyan.modules.studyplan.service.StudyPlanService;
import com.group4.kaoyan.modules.studyplan.service.StudyTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学习计划与推荐接口契约测试。
 *
 * <p>覆盖两部分:
 * <ol>
 *   <li>反射锁定每个 Controller 方法声明的 {@code @SaCheckPermission} 权限码,
 *       防止有人误删鉴权注解或把权限码写错;</li>
 *   <li>用 ObjectMapper 序列化 VO,校验字段名是 camelCase、时间带 +08:00、
 *       字段集合与《共同契约》4.x 节一致 —— 这是前端真正拿到的东西。</li>
 * </ol>
 *
 * <p>standalone MockMvc 不装配 Sa-Token 拦截器,401/403 的真实拦截由
 * 既有全局异常处理器测试覆盖;真实 Token 越权见交付说明的手工联调步骤。</p>
 */
@DisplayName("学习计划与推荐接口契约")
class StudyAndRecommendationApiContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Long USER_ID = 10001L;

    private MockMvc mvc;
    private StudyPlanService studyPlanService;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUpMvc() {
        studyPlanService = mock(StudyPlanService.class);
        recommendationService = mock(RecommendationService.class);
        // Spring Boot 默认 Jackson 关闭 WRITE_DATES_AS_TIMESTAMPS，standalone 需显式对齐
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
                new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        mvc = MockMvcBuilders.standaloneSetup(
                        new StudyPlanController(studyPlanService),
                        new StudyTaskController(mock(StudyTaskService.class)),
                        new RecommendationController(recommendationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    // ============ 权限注解反射校验 ============

    private void assertPermission(Class<?> controller, String methodName, Class<?>[] paramTypes, String perm) throws Exception {
        Method m = controller.getMethod(methodName, paramTypes);
        SaCheckPermission ann = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(ann, controller.getSimpleName() + "." + methodName + " 必须声明 @SaCheckPermission");
        assertTrue(Arrays.asList(ann.value()).contains(perm),
                controller.getSimpleName() + "." + methodName + " 权限码应为 " + perm);
    }

    @Test
    @DisplayName("StudyPlanController.today 声明 study-plan:view")
    void studyPlanTodayPermission() throws Exception {
        assertPermission(StudyPlanController.class, "today", new Class[0],
                PermissionConstants.STUDY_PLAN_VIEW);
    }

    @Test
    @DisplayName("StudyPlanController.generate 声明 study-plan:create")
    void studyPlanGeneratePermission() throws Exception {
        assertPermission(StudyPlanController.class, "generate", new Class[0],
                PermissionConstants.STUDY_PLAN_CREATE);
    }

    @Test
    @DisplayName("StudyTaskController.updateStatus 声明 study-task:update")
    void studyTaskUpdatePermission() throws Exception {
        assertPermission(StudyTaskController.class, "updateStatus",
                new Class[]{Long.class, com.group4.kaoyan.modules.studyplan.dto.StudyTaskUpdateRequest.class},
                PermissionConstants.STUDY_TASK_UPDATE);
    }

    @Test
    @DisplayName("RecommendationController.list 声明 recommendation:view")
    void recommendationListPermission() throws Exception {
        assertPermission(RecommendationController.class, "list",
                new Class[]{String.class, String.class, Long.class, Integer.class},
                PermissionConstants.RECOMMENDATION_VIEW);
    }

    @Test
    @DisplayName("RecommendationController.accept 声明 recommendation:update")
    void recommendationAcceptPermission() throws Exception {
        assertPermission(RecommendationController.class, "accept",
                new Class[]{Long.class},
                PermissionConstants.RECOMMENDATION_UPDATE);
    }

    @Test
    @DisplayName("RecommendationController.dismiss 声明 recommendation:update")
    void recommendationDismissPermission() throws Exception {
        assertPermission(RecommendationController.class, "dismiss",
                new Class[]{Long.class},
                PermissionConstants.RECOMMENDATION_UPDATE);
    }

    // ============ VO 字段契约 ============

    @Test
    @DisplayName("StudyPlanVO 序列化字段与契约 4.2 TodayStudyPlan 对齐")
    void studyPlanVOFieldContract() throws Exception {
        LocalDate planDate = LocalDate.of(2026, 11, 26);
        StudyTaskVO task = new StudyTaskVO(1L, "复习极限", "数学", "极限", 30,
                OffsetDateTime.of(2026, 11, 26, 23, 59, 0, 0, ZoneOffset.ofHours(8)), "TODO", true, 70, 0);
        StudyPlanVO vo = new StudyPlanVO(100L, planDate, "考研计划", planDate.plusDays(30),
                30, new TodayStudySummary(1, 3, 30, 5), List.of(task));

        String json = mapper.writeValueAsString(vo);

        // 契约字段必须存在
        assertTrue(json.contains("\"planId\""), json);
        assertTrue(json.contains("\"planDate\""), json);
        assertTrue(json.contains("\"examDate\""), json);
        assertTrue(json.contains("\"daysUntilExam\""), json);
        assertTrue(json.contains("\"summary\""), json);
        assertTrue(json.contains("\"tasks\""), json);
        // summary 子字段
        assertTrue(json.contains("\"completedTasks\""), json);
        assertTrue(json.contains("\"totalTasks\""), json);
        assertTrue(json.contains("\"studyMinutes\""), json);
        assertTrue(json.contains("\"streakDays\""), json);
        // task 字段
        assertTrue(json.contains("\"overdue\""), json);
        assertTrue(json.contains("\"version\""), json);
        assertTrue(json.contains("\"knowledgePoint\""), json);
        assertTrue(json.contains("\"estimatedMinutes\""), json);
        // 时间格式带 +08:00
        assertTrue(json.contains("+08:00"), "dueAt 应序列化为带 +08:00 的 ISO 字符串: " + json);
        // 不应出现 snake_case 泄漏
        assertFalse(json.contains("plan_date"), "不应有 snake_case 字段: " + json);
        assertFalse(json.contains("study_minutes"), "不应有 snake_case 字段: " + json);
    }

    @Test
    @DisplayName("空计划结构 planId=0 tasks=[]")
    void emptyPlanContract() throws Exception {
        StudyPlanVO vo = new StudyPlanVO(0L, LocalDate.of(2026, 11, 26), "",
                null, null, TodayStudySummary.empty(), List.of());
        String json = mapper.writeValueAsString(vo);
        assertTrue(json.contains("\"planId\":0"), json);
        assertTrue(json.contains("\"tasks\":[]"), json);
    }

    @Test
    @DisplayName("Recommendation 字段与契约 4.3 对齐,时间带 +08:00")
    void recommendationVOFieldContract() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        RecommendationVO rec = new RecommendationVO(1L, "TODAY", "STUDY_TASK",
                "复习极限", "该任务已逾期", "CREATE_TASK", "加入任务", null,
                new BigDecimal("0.9500"), 30, "数学", "极限",
                "ACTIVE", now, now.plusDays(1));

        String json = mapper.writeValueAsString(rec);

        for (String field : List.of("id", "scene", "type", "title", "reason",
                "actionType", "actionLabel", "actionPath", "score",
                "estimatedMinutes", "subject", "knowledgePoint", "status",
                "createdAt", "expiresAt")) {
            assertTrue(json.contains("\"" + field + "\""), "缺少字段 " + field + ": " + json);
        }
        assertTrue(json.contains("+08:00"), "时间应带 +08:00: " + json);
        // snake_case 不应出现
        assertFalse(json.contains("created_at"), json);
        assertFalse(json.contains("action_type"), json);
    }

    @Test
    @DisplayName("RecommendationList 字段与契约 4.4 对齐")
    void recommendationListContract() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        RecommendationVO rec = new RecommendationVO(1L, "TODAY", "STUDY_TASK",
                "t", "r", "CREATE_TASK", "label", null,
                new BigDecimal("0.9"), 20, null, null, "ACTIVE", now, now.plusDays(1));
        RecommendationListVO vo = new RecommendationListVO(List.of(rec), now);
        String json = mapper.writeValueAsString(vo);
        assertTrue(json.contains("\"items\""), json);
        assertTrue(json.contains("\"generatedAt\""), json);
    }

    @Test
    @DisplayName("AcceptRecommendationResult 字段与契约 4.5 对齐")
    void acceptResultContract() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        RecommendationVO rec = new RecommendationVO(1L, "TODAY", "STUDY_TASK",
                "t", "r", "CREATE_TASK", "label", null,
                new BigDecimal("0.9"), 20, "数学", null, "ACCEPTED", now, now.plusDays(1));
        StudyTaskVO task = new StudyTaskVO(500L, "t", "数学", null, 20, null, "TODO", false, 90, 0);
        AcceptRecommendationResult result = new AcceptRecommendationResult(rec, task);
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"recommendation\""), json);
        assertTrue(json.contains("\"task\""), json);
    }

    @Test
    @DisplayName("DismissRecommendationResult 字段与契约 5.6 对齐")
    void dismissResultContract() throws Exception {
        DismissRecommendationResult result = new DismissRecommendationResult(1L, "DISMISSED");
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"recommendationId\""), json);
        assertTrue(json.contains("\"status\":\"DISMISSED\""), json);
    }

    // ============ standalone MockMvc:真实 HTTP 响应体 ============

    @Test
    @DisplayName("GET /study-plans/today → data.planDate/daysUntilExam/summary/tasks[0]")
    void todayEndpointReturnsContractJson() throws Exception {
        LocalDate planDate = LocalDate.of(2026, 11, 26);
        StudyTaskVO task = new StudyTaskVO(1L, "复习极限", "数学", "极限", 30,
                OffsetDateTime.of(2026, 11, 26, 23, 59, 0, 0, ZoneOffset.ofHours(8)),
                "TODO", true, 70, 2);
        StudyPlanVO vo = new StudyPlanVO(100L, planDate, "考研计划",
                planDate.plusDays(30), 30, new TodayStudySummary(1, 3, 30, 0), List.of(task));
        when(studyPlanService.today(USER_ID)).thenReturn(vo);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUserId).thenReturn(USER_ID);
            mvc.perform(get("/api/v1/study-plans/today"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.planDate").value("2026-11-26"))
                    .andExpect(jsonPath("$.data.daysUntilExam").value(30))
                    .andExpect(jsonPath("$.data.summary.completedTasks").value(1))
                    .andExpect(jsonPath("$.data.tasks[0].overdue").value(true))
                    .andExpect(jsonPath("$.data.tasks[0].version").value(2));
        }
    }

    @Test
    @DisplayName("GET /recommendations → data.items / data.generatedAt")
    void recommendationsEndpointReturnsContractJson() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        RecommendationVO rec = new RecommendationVO(1L, "TODAY", "STUDY_TASK",
                "复习极限", "该任务已逾期", "CREATE_TASK", "加入任务", null,
                new BigDecimal("0.95"), 30, "数学", "极限",
                "ACTIVE", now, now.plusDays(1));
        when(recommendationService.list(USER_ID, "TODAY", null, null, null))
                .thenReturn(new RecommendationListVO(List.of(rec), now));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUserId).thenReturn(USER_ID);
            mvc.perform(get("/api/v1/recommendations").param("scene", "TODAY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items[0].title").value("复习极限"))
                    .andExpect(jsonPath("$.data.generatedAt").value("2026-11-26T10:00:00+08:00"));
        }
    }

    @Test
    @DisplayName("未登录调用 /study-plans/today → 401")
    void todayEndpointRequiresLogin() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUserId)
                    .thenThrow(new com.group4.kaoyan.common.BizException(401, "未登录或登录已失效"));
            mvc.perform(get("/api/v1/study-plans/today"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
