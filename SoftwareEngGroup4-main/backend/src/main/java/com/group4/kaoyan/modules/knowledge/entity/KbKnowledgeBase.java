package com.group4.kaoyan.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_knowledge_base")
public class KbKnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 归属用户，数据权限按此过滤 */
    private Long userId;

    /** 0 私有 1 公开 */
    private Integer visibility;

    private Integer docCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
