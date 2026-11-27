package com.group4.kaoyan.modules.studyplan.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.modules.studyplan.dto.StudyPlanVO;
import com.group4.kaoyan.modules.studyplan.service.StudyPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习计划 Controller。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    /**
     * 获取今日学习计划。空计划返回空结构,不返回 404。
     */
    @SaCheckPermission(PermissionConstants.STUDY_PLAN_VIEW)
    @GetMapping("/study-plans/today")
    public R<StudyPlanVO> today() {
        return R.ok(studyPlanService.today(SecurityUtils.currentUserId()));
    }

    /**
     * 生成今日学习计划。已有则返回已有,无目标则 400+10000。
     */
    @SaCheckPermission(PermissionConstants.STUDY_PLAN_CREATE)
    @PostMapping("/study-plans/today/generate")
    public R<StudyPlanVO> generate() {
        return R.ok(studyPlanService.generate(SecurityUtils.currentUserId()));
    }
}
