package com.group4.kaoyan.modules.studyplan.mapper;

import com.group4.kaoyan.modules.studyplan.entity.StudyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习任务 Mapper。更新一律走乐观锁条件 UPDATE。
 */
@Mapper
public interface StudyTaskMapper {

    StudyTask selectById(@Param("id") Long id);

    List<StudyTask> selectByPlanId(@Param("planId") Long planId);

    List<StudyTask> selectByUserAndDate(@Param("userId") Long userId,
                                         @Param("planDate") LocalDate planDate);

    int insert(StudyTask task);

    /**
     * 条件 UPDATE: 必须匹配 userId + version,version 不匹配则影响 0 行。
     * 成功后 version = version + 1。
     */
    int updateWithVersion(StudyTask task, @Param("expectedVersion") int expectedVersion);
}
