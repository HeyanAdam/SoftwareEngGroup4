package com.group4.kaoyan.modules.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.group4.kaoyan.modules.user.entity.UserStudyGoal;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 备考目标 Mapper。更新一律走乐观锁，删除一律逻辑删除。
 */
@Mapper
public interface UserStudyGoalMapper {

    UserStudyGoal selectById(@Param("id") Long id);

    List<UserStudyGoal> selectByUserId(@Param("userId") Long userId, @Param("status") Integer status);

    int insert(UserStudyGoal goal);

    int updateWithVersion(UserStudyGoal goal);

    int logicDelete(@Param("id") Long id, @Param("userId") Long userId);
}
