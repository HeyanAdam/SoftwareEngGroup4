package com.group4.kaoyan.modules.studyreport.mapper;

import com.group4.kaoyan.modules.studyreport.dto.SubjectTaskMetric;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyTaskTotals;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WeeklyReportMetricsMapper {

    List<Long> selectActiveUserIds(@Param("weekStart") LocalDate weekStart,
                                   @Param("weekEnd") LocalDate weekEnd,
                                   @Param("rangeStart") LocalDateTime rangeStart,
                                   @Param("rangeEnd") LocalDateTime rangeEnd);

    WeeklyTaskTotals selectTaskTotals(@Param("userId") Long userId,
                                      @Param("weekStart") LocalDate weekStart,
                                      @Param("weekEnd") LocalDate weekEnd,
                                      @Param("now") LocalDateTime now);

    List<SubjectTaskMetric> selectSubjectMetrics(@Param("userId") Long userId,
                                                  @Param("weekStart") LocalDate weekStart,
                                                  @Param("weekEnd") LocalDate weekEnd,
                                                  @Param("now") LocalDateTime now);

    int countUserQuestions(@Param("userId") Long userId,
                           @Param("rangeStart") LocalDateTime rangeStart,
                           @Param("rangeEnd") LocalDateTime rangeEnd);
}
