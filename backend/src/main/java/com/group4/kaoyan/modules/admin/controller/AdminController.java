package com.group4.kaoyan.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.admin.dto.AdminUserVO;
import com.group4.kaoyan.modules.admin.service.AdminService;
import com.group4.kaoyan.modules.audit.annotation.AuditLog;
import com.group4.kaoyan.modules.audit.entity.LoginLog;
import com.group4.kaoyan.modules.audit.service.LoginLogService;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.knowledge.service.KnowledgeService;
import com.group4.kaoyan.modules.user.entity.SysUser;
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
 * 后台：用户 / 角色 / 权限管理（RBAC 的维护入口）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final KnowledgeService knowledgeService;
    private final LoginLogService loginLogService;

    public record StatusReq(Integer status) {
    }

    public record IdsReq(List<Long> ids) {
    }

    public record RoleCreateReq(String roleCode, String roleName, String description) {
    }

    public record RoleUpdateReq(String roleName, String description) {
    }

    /**
     * 用户分页列表：参数名与《接口设计文档 V1.1》1.7 节一致（page_num / page_size / status）。
     * keyword 是项目扩展的模糊搜索，不影响文档约定。
     */
    @SaCheckPermission("admin:user:list")
    @GetMapping("/users")
    public R<R.PageData<AdminUserVO>> users(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(name = "page_num", defaultValue = "1") long pageNum,
                                            @RequestParam(name = "page_size", defaultValue = "20") long pageSize) {
        return R.ok(adminService.pageUsers(keyword, status, pageNum, pageSize));
    }

    /** 用户详情：文档 13.2 节登记、此前代码缺失，本次补齐（权限码 admin:user:list）。 */
    @SaCheckPermission("admin:user:list")
    @GetMapping("/users/{id}")
    public R<AdminUserVO> userDetail(@PathVariable Long id) {
        return R.ok(adminService.userDetail(id));
    }

    @SaCheckPermission("admin:user:update")
    @AuditLog(module = "admin", action = "UPDATE_STATUS", resourceType = "USER")
    @PutMapping("/users/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        adminService.updateStatus(id, req.status());
        return R.ok();
    }

    @SaCheckPermission("admin:user:update")
    @AuditLog(module = "admin", action = "ASSIGN_ROLES", resourceType = "USER")
    @PutMapping("/users/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody IdsReq req) {
        adminService.assignRoles(StpUtil.getLoginIdAsLong(), id, req.ids());
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
    @AuditLog(module = "admin", action = "UPDATE_ROLE", resourceType = "ROLE")
    @PutMapping("/roles/{id}")
    public R<SysRole> updateRole(@PathVariable Long id, @RequestBody RoleUpdateReq req) {
        return R.ok(adminService.updateRole(id, req.roleName(), req.description()));
    }

    @SaCheckPermission("admin:role:update")
    @AuditLog(module = "admin", action = "DELETE_ROLE", resourceType = "ROLE")
    @DeleteMapping("/roles/{id}")
    public R<Void> deleteRole(@PathVariable Long id) {
        adminService.deleteRole(id);
        return R.ok();
    }

    @SaCheckPermission("admin:user:update")
    @AuditLog(module = "admin", action = "BIND_ROLE", resourceType = "USER")
    @PostMapping("/users/{userId}/roles/{roleId}")
    public R<Void> bindRole(@PathVariable Long userId, @PathVariable Long roleId) {
        adminService.bindRole(userId, roleId);
        return R.ok();
    }

    @SaCheckPermission("admin:user:update")
    @AuditLog(module = "admin", action = "UNBIND_ROLE", resourceType = "USER")
    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public R<Void> unbindRole(@PathVariable Long userId, @PathVariable Long roleId) {
        adminService.unbindRole(StpUtil.getLoginIdAsLong(), userId, roleId);
        return R.ok();
    }

    @SaCheckPermission("admin:role:update")
    @AuditLog(module = "admin", action = "CREATE_ROLE", resourceType = "ROLE")
    @PostMapping("/roles")
    public R<SysRole> createRole(@RequestBody RoleCreateReq req) {
        return R.ok(adminService.createRole(req.roleCode(), req.roleName(), req.description()));
    }

    @SaCheckPermission("admin:role:list")
    @GetMapping("/permissions")
    public R<List<SysPermission>> permissions() {
        return R.ok(adminService.listPermissions());
    }

    @SaCheckPermission("admin:role:list")
    @GetMapping("/roles/{id}/permissions")
    public R<List<String>> rolePermissions(@PathVariable Long id) {
        return R.ok(adminService.permissionCodesOfRole(id));
    }

    @SaCheckPermission("admin:role:update")
    @AuditLog(module = "admin", action = "ASSIGN_PERMISSIONS", resourceType = "ROLE")
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
    @AuditLog(module = "admin", action = "REINDEX_VECTOR", resourceType = "KNOWLEDGE_BASE")
    @PostMapping("/vector/reindex/{kbId}")
    public R<Integer> reindex(@PathVariable Long kbId) {
        return R.ok(knowledgeService.reindexKnowledgeBase(kbId));
    }

    /** 登录日志排查入口：按账号查最近记录。 */
    @SaCheckPermission("admin:user:list")
    @GetMapping("/login-logs")
    public R<List<LoginLog>> loginLogs(@RequestParam(required = false) String account,
                                       @RequestParam(defaultValue = "20") int limit) {
        return R.ok(loginLogService.recent(account, limit));
    }
}
