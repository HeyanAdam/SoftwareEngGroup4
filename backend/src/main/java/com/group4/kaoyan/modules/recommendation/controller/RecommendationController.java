package com.group4.kaoyan.modules.recommendation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.modules.recommendation.dto.*;
import com.group4.kaoyan.modules.recommendation.service.RecommendationService;
import com.group4.kaoyan.platform.idempotency.Idempotent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 推荐 Controller。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 查询推荐。首次无缓存时由 Service 自动生成。
     */
    @SaCheckPermission(PermissionConstants.RECOMMENDATION_VIEW)
    @GetMapping("/recommendations")
    public R<RecommendationListVO> list(@RequestParam String scene,
                                         @RequestParam(required = false) String contextType,
                                         @RequestParam(required = false) Long contextId,
                                         @RequestParam(required = false) Integer limit) {
        return R.ok(recommendationService.list(
                SecurityUtils.currentUserId(), scene, contextType, contextId, limit));
    }

    /**
     * 接受推荐。幂等: 同一 Idempotency-Key 重复请求返回相同结果。
     */
    @Idempotent
    @SaCheckPermission(PermissionConstants.RECOMMENDATION_UPDATE)
    @PostMapping("/recommendations/{id}/accept")
    public R<AcceptRecommendationResult> accept(@PathVariable Long id) {
        return R.ok(recommendationService.accept(SecurityUtils.currentUserId(), id));
    }

    /**
     * 忽略推荐。已忽略重复调用仍返回成功。
     */
    @SaCheckPermission(PermissionConstants.RECOMMENDATION_UPDATE)
    @PostMapping("/recommendations/{id}/dismiss")
    public R<DismissRecommendationResult> dismiss(@PathVariable Long id) {
        return R.ok(recommendationService.dismiss(SecurityUtils.currentUserId(), id));
    }
}
