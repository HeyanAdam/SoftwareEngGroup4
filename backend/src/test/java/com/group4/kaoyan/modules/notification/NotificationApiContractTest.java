package com.group4.kaoyan.modules.notification;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.modules.notification.controller.NotificationController;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.modules.studyreport.controller.WeeklyReportController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("通知与周报 REST 契约")
class NotificationApiContractTest {

    @Test
    @DisplayName("分页 JSON 使用 list/page_num/page_size")
    void pageDataUsesContractFieldNames() throws Exception {
        NotificationVO item = new NotificationVO(
                1L, "TASK_DUE", "WARNING", "任务即将截止", "正文", "/study-plans",
                false, OffsetDateTime.of(2026, 11, 28, 10, 0, 0, 0, ZoneOffset.ofHours(8)),
                null);
        R.PageData<NotificationVO> page = R.PageData.of(1, List.of(item), 1, 20);

        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(R.ok(page)));
        JsonNode data = json.path("data");

        assertTrue(data.has("list"));
        assertTrue(data.has("total"));
        assertTrue(data.has("page_num"));
        assertTrue(data.has("page_size"));
        assertFalse(data.has("items"));
        assertFalse(data.path("list").get(0).has("dedupKey"));
        assertTrue(data.path("list").get(0).path("createdAt").asText().endsWith("+08:00"));
    }

    @Test
    @DisplayName("Controller 权限注解与契约一致")
    void controllersDeclarePermissionAnnotations() throws Exception {
        SaCheckPermission list = NotificationController.class.getMethod(
                        "list", String.class, long.class, long.class)
                .getAnnotation(SaCheckPermission.class);
        SaCheckPermission read = NotificationController.class.getMethod("markRead", Long.class)
                .getAnnotation(SaCheckPermission.class);
        SaCheckPermission report = WeeklyReportController.class.getMethod("current")
                .getAnnotation(SaCheckPermission.class);

        assertArrayEquals(new String[]{PermissionConstants.NOTIFICATION_VIEW},
                list.value());
        assertArrayEquals(new String[]{PermissionConstants.NOTIFICATION_UPDATE},
                read.value());
        assertArrayEquals(new String[]{PermissionConstants.STUDY_PLAN_VIEW},
                report.value());
    }
}
