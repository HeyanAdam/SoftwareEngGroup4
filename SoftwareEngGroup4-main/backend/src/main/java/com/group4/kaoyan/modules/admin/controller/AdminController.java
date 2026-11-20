package com.group4.kaoyan.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.admin.service.AdminService;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.knowledge.service.KnowledgeService;
import com.group4.kaoyan.modules.user.entity.SysUser;
import lombok.RequiredArgsConstructor;
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
 * 后台：用户 / 角色 / 权限管理（RBAC 的维护入口）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final KnowledgeService knowledgeService;

    public record StatusReq(Integer status) {
    }

    public record IdsReq(List<Long> ids) {
    }

    public record RoleCreateReq(String roleCode, String roleName, String description) {
    }

    @SaCheckPermission("admin:user:list")
    @GetMapping("/users")
    public R<R.PageData<SysUser>> users(@RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.pageUsers(keyword, page, size));
    }

    @SaCheckPermission("admin:user:update")
    @PutMapping("/users/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        adminService.updateStatus(id, req.status());
        return R.ok();
    }

    @SaCheckPermission("admin:user:update")
    @PutMapping("/users/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody IdsReq req) {
        adminService.assignRoles(id, req.ids());
        return R.ok();
    }

    @SaCheckPermission("admin:user:list")
    @GetMapping("/users/{id}/roles")
    public R<List<SysRole>> userRoles(@PathVariable Long id) {
        return R.ok(adminService.rolesOfUser(id));
    }

    @SaCheckPermission("admin:role:list")
    @GetMapping("/roles")
    public R<List<SysRole>> roles() {
        return R.ok(adminService.listRoles());
    }

    @SaCheckPermission("admin:role:update")
    @PostMapping("/roles")
    public R<SysRole> createRole(@RequestBody RoleCreateReq req) {
        return R.ok(adminService.createRole(req.roleCode(), req.roleName(), req.description()));
    }

    @SaCheckPermission("admin:role:list")
    @GetMapping("/permissions")
    public R<List<SysPermission>> permissions() {
        return R.ok(adminService.listPermissions());
    }

    @SaCheckPermission("admin:role:update")
    @PutMapping("/roles/{id}/permissions")
    public R<Void> assignPermissions(@PathVariable Long id, @RequestBody IdsReq req) {
        adminService.assignPermissions(id, req.ids());
        return R.ok();
    }

    /**
     * 向量库维护：重建某个知识库的全部向量（清空旧切片/向量后重新入队）。
     * 只有持有 admin:vector:manage 的管理员能调用，普通用户没有任何向量写入口。
     */
    @SaCheckPermission("admin:vector:manage")
    @PostMapping("/vector/reindex/{kbId}")
    public R<Integer> reindex(@PathVariable Long kbId) {
        return R.ok(knowledgeService.reindexKnowledgeBase(kbId));
    }
}
