package com.group4.kaoyan.modules.admin.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardSummaryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员运营大屏：一条请求返回首屏所需的全部统计数据。
 *
 * <p>权限沿用 {@code admin:user:list}：学生没有该权限码（403 + 10008），
 * {@code DATA_AUDITOR} 与 {@code SYSTEM_ADMIN} 默认可读。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardSummaryProvider summaryProvider;

    /**
     * 大屏聚合数据。
     *
     * @param days 趋势天数，只允许 7 / 14 / 30；其它值由服务层返回 400 + 10000
     */
    @SaCheckPermission(PermissionConstants.ADMIN_USER_LIST)
    @GetMapping("/summary")
    public R<AdminDashboardSummaryVO> summary(@RequestParam(defaultValue = "7") int days) {
        return R.ok(summaryProvider.build(days));
    }
}
