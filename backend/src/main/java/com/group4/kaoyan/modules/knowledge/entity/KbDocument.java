package com.group4.kaoyan.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档：原始二进制存 MinIO(objectKey)，解析出的全文持久化在 rawText 字段。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private Long userId;

    private String originalFilename;

    private String fileExt;

    private Long fileSize;

    private String objectKey;

    /** 解析后的纯文本全文（LONGTEXT） */
    private String rawText;

    private String contentHash;

    private Integer chunkCount;

    /** UPLOADED / PARSING / SPLITTING / EMBEDDING / READY / FAILED */
    private String status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
