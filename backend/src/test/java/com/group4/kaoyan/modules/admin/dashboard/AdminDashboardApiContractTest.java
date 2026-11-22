package com.group4.kaoyan.modules.admin.dashboard;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.GlobalExceptionHandler;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.modules.admin.dashboard.controller.AdminDashboardController;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardCardsVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardSummaryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 大屏接口契约测试。
 *
 * <p>用 standalone MockMvc 只跑 Controller + 全局异常处理器，Service 打桩，
 * 因此 CI 不需要 MySQL / Redis，校验的是前端真正拿到的 JSON 结构与错误码。</p>
 *
 * <p>关于越权：standalone MockMvc 不装配 Sa-Token 拦截器，无法在这里真实模拟“学生 403”。
 * 本测试通过反射锁定 {@code @SaCheckPermission("admin:user:list")} 注解契约；
 * 403 + 10008 的映射由既有 {@code GlobalExceptionHandlerTest} 覆盖，
 * 真实 Token 的越权验证见交付说明中的人工联调步骤。</p>
 */
@DisplayName("大屏接口契约")
class AdminDashboardApiContractTest {

    private DashboardSummaryProvider provider;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        provider = mock(DashboardSummaryProvider.class);
        AdminDashboardController controller = new AdminDashboardController(provider);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private AdminDashboardSummaryVO sampleSummary() {
        DashboardCardsVO cards = new DashboardCardsVO();
        cards.setTotalUsers(120L);
        cards.setEnabledUsers(118L);
        cards.setTodayLogins(45L);
        cards.setTodayLoginFailures(3L);
        cards.setFiles(260L);
        cards.setStorageBytes(837124098L);
        cards.setDocuments(18L);
        cards.setFailedDocuments(1L);
        cards.setConversations(64L);
        cards.setChatMessages(980L);
        cards.setTodayChatMessages(120L);

        DashboardTrendVO trend = new DashboardTrendVO();
        trend.setDates(List.of("11-15", "11-16", "11-17", "11-18", "11-19", "11-20", "11-21"));
        trend.setLogins(List.of(10L, 14L, 13L, 20L, 26L, 31L, 45L));
        trend.setUploads(List.of(2L, 5L, 4L, 9L, 12L, 14L, 18L));
        trend.setChatMessages(List.of(30L, 42L, 38L, 65L, 80L, 96L, 120L));

        DashboardSystemVO system = new DashboardSystemVO();
        system.setMysql("UP");
        system.setRedis("DOWN");
        system.setRabbitmq("DOWN");
        system.setDocParseReady(0L);
        system.setDocParseConsumers(1L);
        system.setDocParseDlq(0L);
        system.setOutboxPending(0L);
        system.setOutboxFailed(0L);

        AdminDashboardSummaryVO summary = new AdminDashboardSummaryVO();
        summary.setCards(cards);
        summary.setTrend(trend);
        summary.setSystem(system);
        summary.setGeneratedAt("2026-11-21T14:00:00+08:00");
        return summary;
    }

    @Test
    @DisplayName("days=7 返回前端约定的 camelCase 字段")
    void shouldReturnCamelCaseContract() throws Exception {
        when(provider.build(7)).thenReturn(sampleSummary());

        mvc.perform(get("/api/v1/admin/dashboard/summary").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cards.totalUsers").value(120))
                .andExpect(jsonPath("$.data.cards.todayLoginFailures").value(3))
                .andExpect(jsonPath("$.data.cards.storageBytes").value(837124098))
                .andExpect(jsonPath("$.data.trend.dates[0]").value("11-15"))
                .andExpect(jsonPath("$.data.trend.logins[6]").value(45))
                .andExpect(jsonPath("$.data.system.mysql").value("UP"))
                .andExpect(jsonPath("$.data.system.docParseConsumers").value(1))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-11-21T14:00:00+08:00"));
    }

    @Test
    @DisplayName("days 缺省为 7")
    void shouldDefaultToSevenDays() throws Exception {
        when(provider.build(7)).thenReturn(sampleSummary());

        mvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trend.dates.length()").value(7));
    }

    @Test
    @DisplayName("days=8 返回 400 + 10000")
    void shouldRejectUnsupportedDays() throws Exception {
        when(provider.build(8))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "days 只允许 7、14、30"));

        mvc.perform(get("/api/v1/admin/dashboard/summary").param("days", "8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("响应不包含聊天正文、邮箱、对象存储 key、密码哈希")
    void shouldNotLeakSensitiveFields() throws Exception {
        when(provider.build(7)).thenReturn(sampleSummary());

        mvc.perform(get("/api/v1/admin/dashboard/summary").param("days", "7"))
                .andExpect(jsonPath("$.data.cards.content").doesNotExist())
                .andExpect(jsonPath("$.data.cards.email").doesNotExist())
                .andExpect(jsonPath("$.data.cards.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.cards.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("接口声明 admin:user:list 权限码")
    void shouldDeclareAdminUserListPermission() throws Exception {
        Method method = AdminDashboardController.class.getMethod("summary", int.class);
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);

        assertNotNull(annotation, "summary 必须声明 @SaCheckPermission");
        assertTrue(Arrays.asList(annotation.value()).contains(PermissionConstants.ADMIN_USER_LIST),
                "权限码必须是 " + PermissionConstants.ADMIN_USER_LIST);
    }
}
