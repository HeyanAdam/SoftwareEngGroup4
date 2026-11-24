package com.group4.kaoyan.modules.file.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大文件上传会话（file_upload_session）。
 * 状态：INIT / UPLOADING / MERGING / COMPLETED / FAILED / CANCELLED。
 */
@Data
public class FileUploadSession implements TimedEntity {

    public static final String STATUS_INIT = "INIT";
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_MERGING = "MERGING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long id;
    private Long userId;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private String fileHash;
    private Integer partSize;
    private Integer partCount;
    private Integer uploadedCount;
    /** 已确认落库的分片序号，仅用于接口响应，不映射数据库列。 */
    private List<Integer> uploadedParts;
    /** 尚未确认落库的分片序号，仅用于接口响应，不映射数据库列。 */
    private List<Integer> missingParts;
    private String bucketName;
    private String objectKey;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
