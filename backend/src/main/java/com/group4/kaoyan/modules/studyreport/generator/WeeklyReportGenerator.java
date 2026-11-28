package com.group4.kaoyan.modules.studyreport.generator;

import com.group4.kaoyan.modules.studyreport.dto.WeeklyMetrics;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyReportContent;

public interface WeeklyReportGenerator {

    WeeklyReportContent generate(WeeklyMetrics metrics);
}
