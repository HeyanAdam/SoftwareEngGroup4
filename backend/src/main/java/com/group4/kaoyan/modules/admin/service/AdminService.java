package com.group4.kaoyan.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.R.PageData;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    /** 管理员角色码，与 RbacInitializer 保持一致 */
    private static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    public PageData<SysUser> pageUsers(String keyword, long page, long size) {
        Page<SysUser> p = new Page<>(page, size);
        IPage<SysUser> result = userMapper.selectPage(p, Wrappers.<SysUser>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId));
        return PageData.of(result.getTotal(), result.getRecords());
    }

    public void updateStatus(Long userId, Integer status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException("用户不存在");
        }
        // 兜底：不能把系统里最后一个管理员降权，否则谁都没法再进后台
        boolean targetIsAdmin = roleMapper.selectRoleCodesByUserId(userId).contains(ROLE_ADMIN);
        boolean keepAdmin = roleIds != null && roleIds.stream()
                .map(roleMapper::selectById)
                .anyMatch(r -> r != null && ROLE_ADMIN.equals(r.getRoleCode()));
        if (targetIsAdmin && !keepAdmin && roleMapper.countUsersWithRoleCode(ROLE_ADMIN) <= 1) {
            throw new BizException("这是当前唯一的系统管理员，不能降权；请先把其他用户提权");
        }
        roleMapper.unbindUserRoles(userId);
        if (roleIds != null) {
            roleIds.forEach(rid -> roleMapper.bindUserRole(userId, rid));
        }
    }

    public List<SysRole> rolesOfUser(Long userId) {
        return roleMapper.selectByUserId(userId);
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
    }

    @Transactional
    public SysRole createRole(String roleCode, String roleName, String description) {
        if (roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode)) > 0) {
            throw new BizException("角色标识已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        roleMapper.insert(role);
        return role;
    }

    public List<SysPermission> listPermissions() {
        return permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery().orderByAsc(SysPermission::getId));
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException("角色不存在");
        }
        permissionMapper.unbindRolePermissions(roleId);
        if (permissionIds != null) {
            permissionIds.forEach(pid -> permissionMapper.bindRolePermission(roleId, pid));
        }
    }

    public List<String> permissionCodesOfRole(Long roleId) {
        return permissionMapper.selectPermCodesByRoleId(roleId);
    }
}
