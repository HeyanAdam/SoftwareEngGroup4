package com.group4.kaoyan.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document_chunk")
public class KbDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Long kbId;

    private Long userId;

    private Integer chunkIndex;

    private String content;

    /** Milvus 中的主键，与业务表 id 保持一致，便于双向定位 */
    private Long milvusId;

    private Integer charCount;

    private LocalDateTime createTime;
}
