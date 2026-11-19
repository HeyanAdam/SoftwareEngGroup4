package com.group4.kaoyan.modules.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.user.dto.StudyGoalDTO;
import com.group4.kaoyan.modules.user.dto.UserProfileDTO;
import com.group4.kaoyan.modules.user.entity.UserProfile;
import com.group4.kaoyan.modules.user.entity.UserStudyGoal;
import com.group4.kaoyan.modules.user.service.UserProfileService;
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
 * 用户画像与备考目标（持久化到 user_profile / user_study_goal）。
 *
 * <p>与旧的 /api/user/profile 并存：旧接口维护 sys_user 的昵称/邮箱/头像，
 * 这里只管画像与目标，互不影响。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    @GetMapping("/profile")
    public R<UserProfile> profile() {
        return R.ok(profileService.getProfile(currentUserId()));
    }

    @PutMapping("/profile")
    public R<UserProfile> saveProfile(@RequestBody UserProfileDTO dto) {
        return R.ok(profileService.saveProfile(currentUserId(), dto));
    }

    @GetMapping("/goals")
    public R<List<UserStudyGoal>> goals(@RequestParam(required = false) Integer status) {
        return R.ok(profileService.listGoals(currentUserId(), status));
    }

    @Idempotent
    @PostMapping("/goals")
    public R<UserStudyGoal> createGoal(@RequestBody StudyGoalDTO dto) {
        return R.ok(profileService.createGoal(currentUserId(), dto));
    }

    @PutMapping("/goals/{id}")
    public R<UserStudyGoal> updateGoal(@PathVariable Long id, @RequestBody StudyGoalDTO dto) {
        return R.ok(profileService.updateGoal(currentUserId(), id, dto));
    }

    @DeleteMapping("/goals/{id}")
    public R<Void> deleteGoal(@PathVariable Long id) {
        profileService.deleteGoal(currentUserId(), id);
        return R.ok();
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
