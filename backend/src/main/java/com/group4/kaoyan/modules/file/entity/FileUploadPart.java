package com.group4.kaoyan.modules.file.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传分片（file_upload_part）。唯一键 (session_id, part_number)。
 */
@Data
public class FileUploadPart implements TimedEntity {

    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private Long sessionId;
    private Integer partNumber;
    private Integer partSize;
    private String partHash;
    private String bucketName;
    private String objectKey;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
