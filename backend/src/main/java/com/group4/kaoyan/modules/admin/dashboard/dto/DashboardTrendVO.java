package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

import java.util.List;

/**
 * 运营大屏趋势数据。
 *
 * <p>四条数组长度必须完全一致，由服务端统一补齐缺失日期：</p>
 * <ul>
 *   <li>{@code dates}：格式 {@code MM-dd}，按时间正序，最后一项是今天。</li>
 *   <li>{@code logins}：每日<b>成功</b>登录数（口径与 {@code todayLogins} 一致）。</li>
 *   <li>{@code uploads}：每日文件上传数，以 {@code file_object.create_time} 为准。</li>
 *   <li>{@code chatMessages}：每日聊天消息数，以 {@code chat_message.create_time} 为准。</li>
 * </ul>
 */
@Data
public class DashboardTrendVO {

    private List<String> dates;
    private List<Long> logins;
    private List<Long> uploads;
    private List<Long> chatMessages;
}
