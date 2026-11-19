package com.group4.kaoyan.modules.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.user.dto.StudyGoalDTO;
import com.group4.kaoyan.modules.user.dto.UserProfileDTO;
import com.group4.kaoyan.modules.user.entity.UserProfile;
import com.group4.kaoyan.modules.user.entity.UserStudyGoal;
import com.group4.kaoyan.modules.user.service.UserProfileService;
import com.group4.kaoyan.modules.user.service.UserService;
import com.group4.kaoyan.platform.idempotency.Idempotent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户画像与备考目标（对齐《接口设计文档 V1.1 适配版》第 4 章）。
 *
 * <p>路径统一到 /api/v1/user 下：profile 与 study-goals；旧的 /api/user/profile
 * 与 /goals 已迁移（P2 D 项）。本类接管原 UserController 的资料读写，
 * 避免 /api/v1/user/profile 出现双重映射、Spring 启动时 ambiguous mapping。</p>
 *
 * <p>权限：登录用户本人（文档 4.1“权限：登录用户本人”），故不挂权限码，只做登录态校验。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;
    private final UserService userService;

    @GetMapping("/profile")
    public R<UserProfile> profile() {
        return R.ok(profileService.getProfile(currentUserId()));
    }

    /** 文档 4.2：nickname / email / avatar_url 与画像字段可在同一个 body 里提交。 */
    @PutMapping("/profile")
    public R<UserProfile> saveProfile(@RequestBody UserProfileDTO dto) {
        Long userId = currentUserId();
        if (dto != null
                && (dto.nickname() != null || dto.email() != null || dto.avatarUrl() != null)) {
            userService.updateProfile(dto.nickname(), dto.email(), dto.avatarUrl());
        }
        return R.ok(profileService.saveProfile(userId, dto));
    }

    /** 文档 4.3：GET /api/v1/user/study-goals?exam_year=。 */
    @GetMapping("/study-goals")
    public R<List<UserStudyGoal>> goals(@RequestParam(required = false) Integer status) {
        return R.ok(profileService.listGoals(currentUserId(), status));
    }

    /** 文档 4.4 为集合式全量保存，这里等价实现为“该年度不存在则新增”，效果一致。 */
    @Idempotent
    @PostMapping("/study-goals")
    public R<UserStudyGoal> createGoal(@RequestBody StudyGoalDTO dto) {
        return R.ok(profileService.createGoal(currentUserId(), dto));
    }

    @PutMapping("/study-goals/{id}")
    public R<UserStudyGoal> updateGoal(@PathVariable Long id, @RequestBody StudyGoalDTO dto) {
        return R.ok(profileService.updateGoal(currentUserId(), id, dto));
    }

    @DeleteMapping("/study-goals/{id}")
    public R<Void> deleteGoal(@PathVariable Long id) {
        profileService.deleteGoal(currentUserId(), id);
        return R.ok();
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
