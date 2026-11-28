package com.group4.kaoyan.modules.notification.mapper;

import com.group4.kaoyan.modules.notification.dto.DailyPlanReminderSource;
import com.group4.kaoyan.modules.notification.dto.ExamReminderSource;
import com.group4.kaoyan.modules.notification.dto.TaskReminderSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReminderSourceMapper {

    List<DailyPlanReminderSource> selectDailyPlanSources(@Param("planDate") LocalDate planDate);

    List<TaskReminderSource> selectDueTaskSources(@Param("now") LocalDateTime now,
                                                  @Param("limit") int limit);

    List<TaskReminderSource> selectOverdueTaskSources(@Param("now") LocalDateTime now,
                                                      @Param("limit") int limit);

    List<ExamReminderSource> selectExamSources(@Param("today") LocalDate today);
}
