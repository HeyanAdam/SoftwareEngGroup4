package com.group4.kaoyan.modules.chat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;

import java.util.List;

/**
 * 聊天历史分页结果。
 *
 * <p>{@code items} 按时间升序（与旧接口数组顺序一致），
 * {@code next_before_id} 是继续向前翻页应传给 {@code before_id} 的游标（当前页最早消息 ID），
 * 没有更早消息时为 {@code null}。</p>
 */
public record ChatHistoryPage(
        List<ChatMessage> items,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("next_before_id") Long nextBeforeId
) {
}
