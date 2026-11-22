package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 运营大屏 KPI 卡片。
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li>{@code totalUsers} / {@code enabledUsers}：{@code sys_user} 全量 / {@code status = 1}；
 *       该表没有逻辑删除字段，不要额外加 {@code deleted = 0}。</li>
 *   <li>{@code todayLogins} / {@code todayLoginFailures}：今日 {@code sys_login_log}
 *       成功 / 失败条数，时间范围按 Asia/Shanghai 的 [今日 00:00, 明日 00:00)。</li>
 *   <li>{@code files} / {@code storageBytes}：{@code file_object} 中 {@code deleted = 0} 的文件数与字节总数。</li>
 *   <li>{@code documents} / {@code failedDocuments}：{@code kb_document} 总数 / {@code status = 'FAILED'} 数。</li>
 *   <li>{@code conversations}：{@code chat_conversation} 中 {@code deleted = 0} 的会话数。</li>
 *   <li>{@code chatMessages} / {@code todayChatMessages}：{@code chat_message} 总数 / 今日条数。</li>
 * </ul>
 *
 * <p>所有计数使用 {@link Long}，字节数同样使用 {@link Long}，前端按 number 接收。</p>
 */
@Data
public class DashboardCardsVO {

    private Long totalUsers;
    private Long enabledUsers;
    private Long todayLogins;
    private Long todayLoginFailures;
    private Long files;
    private Long storageBytes;
    private Long documents;
    private Long failedDocuments;
    private Long conversations;
    private Long chatMessages;
    private Long todayChatMessages;
}
