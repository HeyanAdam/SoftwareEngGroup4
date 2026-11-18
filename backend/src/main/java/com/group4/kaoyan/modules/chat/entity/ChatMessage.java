package com.group4.kaoyan.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    /** user / assistant / system */
    private String role;

    private String content;

    /** 引用片段的 JSON，便于前端展示来源 */
    private String referencesJson;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private LocalDateTime createTime;
}
