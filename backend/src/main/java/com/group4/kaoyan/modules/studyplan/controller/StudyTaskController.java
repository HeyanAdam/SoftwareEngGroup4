package com.group4.kaoyan.modules.studyplan.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskUpdateRequest;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;
import com.group4.kaoyan.modules.studyplan.dto.StudyTaskWriteRequest;
import com.group4.kaoyan.modules.studyplan.service.StudyTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学习任务 Controller。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudyTaskController {

    private final StudyTaskService studyTaskService;

    @SaCheckPermission(PermissionConstants.STUDY_PLAN_CREATE)
    @PostMapping("/study-tasks")
    public R<StudyTaskVO> create(@RequestBody StudyTaskWriteRequest req) {
        return R.ok(studyTaskService.create(SecurityUtils.currentUserId(), req));
    }

    @SaCheckPermission(PermissionConstants.STUDY_TASK_UPDATE)
    @PutMapping("/study-tasks/{id}")
    public R<StudyTaskVO> update(@PathVariable Long id,
                                 @RequestBody StudyTaskWriteRequest req) {
        return R.ok(studyTaskService.updateDetails(SecurityUtils.currentUserId(), id, req));
    }

    /**
     * 修改任务状态。乐观锁条件 UPDATE,版本不匹配 409。
     */
    @SaCheckPermission(PermissionConstants.STUDY_TASK_UPDATE)
    @PatchMapping("/study-tasks/{id}")
    public R<StudyTaskVO> updateStatus(@PathVariable Long id,
                                       @RequestBody StudyTaskUpdateRequest req) {
        return R.ok(studyTaskService.updateStatus(SecurityUtils.currentUserId(), id, req));
    }

    @SaCheckPermission(PermissionConstants.STUDY_TASK_UPDATE)
    @DeleteMapping("/study-tasks/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestParam Integer expectedVersion) {
        studyTaskService.delete(SecurityUtils.currentUserId(), id, expectedVersion);
        return R.ok();
    }
}
