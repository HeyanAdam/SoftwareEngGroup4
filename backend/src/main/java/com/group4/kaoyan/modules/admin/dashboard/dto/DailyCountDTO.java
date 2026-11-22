package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 按日期聚合的计数行（趋势 SQL 的返回结构）。
 *
 * <p>{@code statDate} 由 SQL 的 {@code DATE_FORMAT(create_time, '%m-%d')} 生成，
 * {@code total} 为该日计数；数据库只返回有数据的日期，缺失日期由服务层补 0。</p>
 */
@Data
public class DailyCountDTO {

    private String statDate;
    private Long total;
}
