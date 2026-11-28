package com.group4.kaoyan.modules.studyreport.mapper;

import com.group4.kaoyan.modules.studyreport.entity.StudyWeeklyReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface WeeklyReportMapper {

    StudyWeeklyReport selectByUserAndWeek(@Param("userId") Long userId,
                                          @Param("weekStart") LocalDate weekStart);

    int insert(StudyWeeklyReport report);

    int update(StudyWeeklyReport report);
}
