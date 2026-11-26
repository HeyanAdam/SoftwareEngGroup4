package com.group4.kaoyan.modules.studyplan.mapper;

import com.group4.kaoyan.modules.studyplan.entity.StudyPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 学习计划 Mapper。
 */
@Mapper
public interface StudyPlanMapper {

    StudyPlan selectById(@Param("id") Long id);

    StudyPlan selectByUserAndDate(@Param("userId") Long userId,
                                  @Param("planDate") LocalDate planDate);

    int insert(StudyPlan plan);

    int updateWithVersion(StudyPlan plan);
}
