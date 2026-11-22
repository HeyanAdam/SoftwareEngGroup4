package com.group4.kaoyan.modules.admin.dashboard.service;

import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;

/**
 * 大屏业务统计提供者（丙与后端统计负责人之间的冻结契约）。
 *
 * <p>职责边界：本接口只产出 {@code cards} 与 {@code trend}，<b>不产出</b> {@code system} 与
 * {@code generatedAt}——系统探活、队列、Outbox 指标由可观测性侧（丙）统一注入，
 * 这样缓存层缓存的是「业务数据 + 真实指标」的整体快照，不需要二次拼装。</p>
 *
 * <p>实现方（甲）负责用户/登录/文件/文档/聊天统计与 7/14/30 天补零趋势；
 * 未实现时 {@code DashboardSummaryService} 会退化为全 0 的合法结果，
 * 绝不编造数据、也不返回假 UP。</p>
 */
public interface DashboardSummaryProvider {

    /**
     * 构建业务统计部分。
     *
     * @param days 趋势天数，只可能是 7 / 14 / 30（调用方已校验）
     * @return cards 与 trend 已填充；system / generatedAt 可为空
     */
    AdminDashboardSummaryVO buildBusiness(int days);
}
