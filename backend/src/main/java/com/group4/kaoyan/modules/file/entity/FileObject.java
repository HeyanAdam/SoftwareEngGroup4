package com.group4.kaoyan.modules.file.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件对象元数据（file_object）。文件本体在 MinIO，库里只存元数据。
 */
@Data
public class FileObject implements TimedEntity {

    private Long id;
    private Long ownerUserId;
    /** AVATAR / QUESTION_IMAGE / STUDY_MATERIAL / KNOWLEDGE_DOCUMENT / CHAT_ATTACHMENT */
    private String scene;
    private String bucketName;
    private String objectKey;
    private String originalFilename;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String sha256;
    /** READY / FAILED */
    private String status;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
