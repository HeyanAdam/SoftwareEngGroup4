package com.group4.kaoyan.modules.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户画像的最小读取入口，只用于登录/当前用户接口的 has_profile 判断。
 * 画像的完整增删改由后续「用户画像与备考目标」模块负责。
 */
@Mapper
public interface UserProfileMapper {

    @Select("SELECT COUNT(*) FROM user_profile WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);
}
