package com.group4.kaoyan.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** 限定检索的知识库，可为空表示检索本人全部知识库 */
    private Long kbId;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
