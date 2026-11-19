package com.group4.kaoyan.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int bindUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int unbindUserRoles(@Param("userId") Long userId);

    /** 某个角色下有多少个用户（用于判断"系统里还没有管理员"） */
    @Select("SELECT COUNT(DISTINCT ur.user_id) FROM sys_user_role ur "
            + "JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = #{roleCode}")
    long countUsersWithRoleCode(@Param("roleCode") String roleCode);

    /** 某个角色绑定了哪些用户（旧角色码迁移用） */
    @Select("SELECT DISTINCT ur.user_id FROM sys_user_role ur WHERE ur.role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    /** 解除某个角色上的全部用户绑定（旧角色码迁移用） */
    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int unbindUsersByRoleId(@Param("roleId") Long roleId);
}
