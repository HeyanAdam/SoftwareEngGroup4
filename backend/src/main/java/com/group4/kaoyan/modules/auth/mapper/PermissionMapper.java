package com.group4.kaoyan.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {

    /** 用户的权限码（合并多角色、去重；停用角色不参与鉴权，所以联查时过滤 status）。 */
    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p "
            + "JOIN sys_role_permission rp ON p.id = rp.permission_id "
            + "JOIN sys_user_role ur ON rp.role_id = ur.role_id "
            + "JOIN sys_role r ON r.id = rp.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.* FROM sys_permission p "
            + "JOIN sys_role_permission rp ON p.id = rp.permission_id "
            + "JOIN sys_user_role ur ON rp.role_id = ur.role_id "
            + "JOIN sys_role r ON r.id = rp.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<SysPermission> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT p.perm_code FROM sys_permission p "
            + "JOIN sys_role_permission rp ON p.id = rp.permission_id "
            + "WHERE rp.role_id = #{roleId}")
    List<String> selectPermCodesByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT IGNORE INTO sys_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permissionId})")
    int bindRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int unbindRolePermissions(@Param("roleId") Long roleId);
}
