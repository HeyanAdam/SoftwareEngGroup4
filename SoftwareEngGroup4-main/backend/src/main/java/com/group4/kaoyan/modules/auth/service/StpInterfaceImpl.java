package com.group4.kaoyan.modules.auth.service;

import cn.dev33.satoken.stp.StpInterface;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源：从 sys_user_role / sys_role_permission 读取，
 * 这样 @SaCheckRole / @SaCheckPermission 就能直接生效。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionMapper.selectPermCodesByUserId(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleMapper.selectRoleCodesByUserId(Long.valueOf(loginId.toString()));
    }
}
