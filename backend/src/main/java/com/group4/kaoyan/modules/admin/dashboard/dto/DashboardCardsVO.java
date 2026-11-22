package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 大屏顶部 KPI 卡片。
 *
 * <p>字段与前端 {@code frontend/src/features/admin/dashboard/types.ts} 的
 * {@code DashboardCards} 一一对应（契约见《数字大屏_甲_后端统计接口》第 4 节 C1）。
 * 所有计数用 {@code Long}，字节数也用 {@code Long}，避免前端大数精度问题。</p>
 */
@Data
public class DashboardCardsVO {

    /** 用户总数（逻辑删除不计）。 */
    private Long totalUsers;

    /** 启用状态用户数。 */
    private Long enabledUsers;

    /** 今日成功登录数。 */
    private Long todayLogins;

    /** 今日失败登录数。 */
    private Long todayLoginFailures;

    /** 文件对象总数（逻辑删除不计）。 */
    private Long files;

    /** 文件占用字节总数，空表为 0 而不是 null。 */
    private Long storageBytes;

    /** 知识文档总数。 */
    private Long documents;

    /** 解析失败的知识文档数。 */
    private Long failedDocuments;

    /** 会话总数。 */
    private Long conversations;

    /** 消息总数。 */
    private Long chatMessages;

    /** 今日消息数。 */
    private Long todayChatMessages;

    /** 空数据卡片：所有计数为 0，用于统计接口未接入时的安全降级。 */
    public static DashboardCardsVO empty() {
        DashboardCardsVO cards = new DashboardCardsVO();
        cards.setTotalUsers(0L);
        cards.setEnabledUsers(0L);
        cards.setTodayLogins(0L);
        cards.setTodayLoginFailures(0L);
        cards.setFiles(0L);
        cards.setStorageBytes(0L);
        cards.setDocuments(0L);
        cards.setFailedDocuments(0L);
        cards.setConversations(0L);
        cards.setChatMessages(0L);
        cards.setTodayChatMessages(0L);
        return cards;
    }
}
