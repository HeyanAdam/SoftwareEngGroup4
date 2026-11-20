package com.group4.kaoyan.modules.audit.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.group4.kaoyan.modules.audit.entity.LoginLog;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginLogMapper {

    int insert(LoginLog log);

    /** 最近若干条登录记录，供后台排查（按时间倒序）。 */
    java.util.List<LoginLog> selectRecent(@Param("account") String account, @Param("limit") int limit);
}
