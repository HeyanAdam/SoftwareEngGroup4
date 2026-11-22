package com.group4.kaoyan.modules.admin.dashboard;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.GlobalExceptionHandler;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.modules.admin.dashboard.controller.AdminDashboardController;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardCardsVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardSummaryProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 步骤 E6 的契约测试：路径、days 校验、权限码、响应字段名与敏感字段。
 *
 * <p>说明：登录态与 401/403 由 Sa-Token 拦截器执行，standalone MockMvc 不装载拦截器，
 * 因此「学生 403 + 10008」「未登录 401」在真实环境按验收记录 E8 手工/接口验证；
 * 这里用注解断言锁住权限码，防止后续被改成裸奔接口。</p>
 */
@DisplayName("大屏接口契约")
class AdminDashboardContractTest {

    private final DashboardSummaryProvider service = mock(DashboardSummaryProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminDashboardController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private AdminDashboardSummaryVO sample() {
        AdminDashboardSummaryVO vo = new AdminDashboardSummaryVO();
        DashboardCardsVO cards = DashboardCardsVO.empty();
        cards.setTotalUsers(120L);
        vo.setCards(cards);
        DashboardTrendVO trend = new DashboardTrendVO();
        trend.setDates(List.of("11-15", "11-16"));
        trend.setLogins(List.of(1L, 2L));
        trend.setUploads(List.of(0L, 0L));
        trend.setChatMessages(List.of(3L, 4L));
        vo.setTrend(trend);
        vo.setSystem(DashboardSystemVO.down());
        vo.setGeneratedAt("2026-11-21T14:00:00+08:00");
        return vo;
    }

    @Test
    @DisplayName("GET /admin/dashboard/summary?days=7 返回 200 与统一响应体")
    void summaryReturnsWrappedData() throws Exception {
        when(service.build(7)).thenReturn(sample());

        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .param("days", "7")
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cards.totalUsers").value(120))
                .andExpect(jsonPath("$.data.system.mysql").value("DOWN"))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-11-21T14:00:00+08:00"));
    }

    @Test
    @DisplayName("days=8 等非法值：400 + 10000")
    void invalidDaysReturns400() throws Exception {
        when(service.build(anyInt()))
                .thenThrow(new com.group4.kaoyan.common.BizException(
                        com.group4.kaoyan.common.ErrorCode.PARAM_INVALID, "days 只允许 7、14、30"));

        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .param("days", "8")
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    @DisplayName("权限码与《数字大屏_甲》一致：admin:user:list（DATA_AUDITOR 可用，学生 403）")
    void summaryIsGuardedByAdminUserListPermission() throws Exception {
        SaCheckPermission annotation = AdminDashboardController.class
                .getMethod("summary", int.class)
                .getAnnotation(SaCheckPermission.class);

        assertNotNull(annotation, "大屏接口必须声明 @SaCheckPermission");
        assertTrue(List.of(annotation.value()).contains(PermissionConstants.ADMIN_USER_LIST),
                "大屏接口权限码应为 admin:user:list");
        assertEquals("admin:user:list", PermissionConstants.ADMIN_USER_LIST);
    }

    @Test
    @DisplayName("响应字段名与前端契约一致，且不含聊天正文/邮箱/payload 等敏感字段")
    void responseMatchesFrontendContractWithoutSensitiveFields() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(sample()));

        assertTrue(root.has("cards") && root.has("trend") && root.has("system")
                && root.has("generatedAt"));

        List<String> cardFields = List.of("totalUsers", "enabledUsers", "todayLogins",
                "todayLoginFailures", "files", "storageBytes", "documents", "failedDocuments",
                "conversations", "chatMessages", "todayChatMessages");
        for (String field : cardFields) {
            assertTrue(root.get("cards").has(field), "cards 缺少字段：" + field);
        }

        List<String> systemFields = List.of("mysql", "redis", "rabbitmq", "docParseReady",
                "docParseConsumers", "docParseDlq", "outboxPending", "outboxFailed");
        for (String field : systemFields) {
            assertTrue(root.get("system").has(field), "system 缺少字段：" + field);
        }

        for (String forbidden : List.of("content", "email", "payloadJson", "payload",
                "passwordHash", "accessKey", "secretKey")) {
            assertFalse(containsField(root, forbidden), "响应不应包含敏感字段：" + forbidden);
        }
    }

    private static boolean containsField(JsonNode node, String name) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String current = names.next();
                if (current.equalsIgnoreCase(name) || containsField(node.get(current), name)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsField(child, name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
