package com.group4.kaoyan.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group4.kaoyan.modules.auth.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
