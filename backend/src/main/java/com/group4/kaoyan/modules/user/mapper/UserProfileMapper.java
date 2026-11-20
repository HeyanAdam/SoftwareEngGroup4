package com.group4.kaoyan.modules.user.mapper;

import com.group4.kaoyan.modules.user.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 画像 Mapper：接口方法与 XML 必须成对，缺语句启动即报 BindingException。
 *
 * <p>selectByUserId / insert / updateByUserId 的 SQL 在 {@code mapper/user/UserProfileMapper.xml}；
 * countByUserId 是 P1 认证契约要求的轻量查询（登录与 /me 的 has_profile 判断），注解内联即可。</p>
 */
@Mapper
public interface UserProfileMapper {

    UserProfile selectByUserId(@Param("userId") Long userId);

    int insert(UserProfile profile);

    int updateByUserId(UserProfile profile);

    /** 该用户是否已填过画像（只计数，不加载整行）。 */
    @Select("SELECT COUNT(*) FROM user_profile WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);
}
