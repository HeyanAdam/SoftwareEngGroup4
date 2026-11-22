package com.group4.kaoyan.modules.admin.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.platform.dashboard.service.DashboardSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员运营大屏聚合接口。
 *
 * <p>契约（与前端 {@code dashboard.api.ts} 一致）：</p>
 * <pre>
 * GET /api/v1/admin/dashboard/summary?days=7|14|30
 * 权限码 admin:user:list —— DATA_AUDITOR 只读可用，学生 403 + 10008
 * </pre>
 *
 * <p>这里只做参数透传与权限闸门，缓存、统计、探活、降级全部在
 * {@link DashboardSummaryService} 内完成；Controller 不重复拼探活逻辑。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardSummaryService dashboardSummaryService;

    /**
     * 大屏一次性聚合接口：一次请求返回 KPI、趋势与系统指标。
     *
     * @param days 趋势天数，只允许 7 / 14 / 30，其他值返回 400 + 10000
     */
    @SaCheckPermission(PermissionConstants.ADMIN_USER_LIST)
    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public R<AdminDashboardSummaryVO> summary(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardSummaryService.summary(days));
    }
}
