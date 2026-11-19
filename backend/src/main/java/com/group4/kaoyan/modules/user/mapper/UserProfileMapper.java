package com.group4.kaoyan.modules.user.mapper;

import com.group4.kaoyan.modules.user.entity.UserProfile;
import org.apache.ibatis.annotations.Param;

/**
 * 画像 Mapper：接口方法与 XML 必须成对，缺语句启动即报 BindingException。
 */
public interface UserProfileMapper {

    UserProfile selectByUserId(@Param("userId") Long userId);

    int insert(UserProfile profile);

    int updateByUserId(UserProfile profile);
}
