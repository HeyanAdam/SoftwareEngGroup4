package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 大屏趋势数据。
 *
 * <p>{@code dates} 与 {@code logins} / {@code uploads} / {@code chatMessages}
 * 长度必须完全一致，缺失日期由后端补 0，前端按下标一一对应。</p>
 */
@Data
public class DashboardTrendVO {

    /** 日期标签，格式 MM-dd。 */
    private List<String> dates = new ArrayList<>();

    /** 成功登录数趋势。 */
    private List<Long> logins = new ArrayList<>();

    /** 文件上传数趋势。 */
    private List<Long> uploads = new ArrayList<>();

    /** 聊天消息数趋势。 */
    private List<Long> chatMessages = new ArrayList<>();
}
