package com.group4.kaoyan.modules.studyreport.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyStudyReportSummaryVO;
import com.group4.kaoyan.modules.studyreport.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    @SaCheckPermission(PermissionConstants.STUDY_PLAN_VIEW)
    @GetMapping("/study-reports/weekly/current")
    public R<WeeklyStudyReportSummaryVO> current() {
        return R.ok(weeklyReportService.current(SecurityUtils.currentUserId()));
    }
}
