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
}
