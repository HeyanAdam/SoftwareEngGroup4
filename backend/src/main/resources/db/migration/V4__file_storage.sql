-- V4：文件对象、大文件上传会话与上传分片
-- 对应接口文档 V1.1 第 10 章；错误码段 30xxx
-- 约定：主键 BIGINT 自增；时间为 DATETIME；文件大小 BIGINT；哈希为 SHA-256 十六进制 CHAR(64)

CREATE TABLE IF NOT EXISTS file_object (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id     BIGINT       NOT NULL COMMENT '归属用户，来自登录态，不接受前端传入',
  scene             VARCHAR(32)  NOT NULL COMMENT 'AVATAR/QUESTION_IMAGE/STUDY_MATERIAL/KNOWLEDGE_DOCUMENT/CHAT_ATTACHMENT',
  bucket_name       VARCHAR(64)  NOT NULL COMMENT 'MinIO 桶名',
  object_key        VARCHAR(512) NOT NULL COMMENT '对象键，不含桶名',
  original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_ext          VARCHAR(16)  NULL COMMENT '扩展名，小写不带点',
  content_type      VARCHAR(128) NOT NULL COMMENT 'MIME 类型，下载时回填响应头',
  file_size         BIGINT       NOT NULL COMMENT '文件字节数',
  sha256            CHAR(64)     NULL COMMENT 'SHA-256 十六进制，用于 ETag 与秒传',
  status            VARCHAR(32)  NOT NULL DEFAULT 'READY' COMMENT 'READY 可用 / FAILED 写入或校验失败',
  deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_file_object_key (bucket_name, object_key),
  KEY idx_file_owner (owner_user_id),
  KEY idx_file_hash (owner_user_id, sha256),
  KEY idx_file_scene_status (scene, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件对象元数据';

CREATE TABLE IF NOT EXISTS file_upload_session (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT       NOT NULL COMMENT '上传用户',
  original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_size         BIGINT       NOT NULL COMMENT '文件总字节数',
  content_type      VARCHAR(128) NOT NULL COMMENT 'MIME 类型',
  file_hash         CHAR(64)     NULL COMMENT '整文件 SHA-256，完成时校验与秒传',
  part_size         INT          NOT NULL COMMENT '分片大小（字节）',
  part_count        INT          NOT NULL COMMENT '分片总数',
  uploaded_count    INT          NOT NULL DEFAULT 0 COMMENT '已上传分片数',
  bucket_name       VARCHAR(64)  NOT NULL COMMENT 'MinIO 桶名',
  object_key        VARCHAR(512) NULL COMMENT '合并后的正式对象键，完成前为空',
  status            VARCHAR(32)  NOT NULL DEFAULT 'INIT' COMMENT 'INIT/UPLOADING/MERGING/COMPLETED/FAILED/CANCELLED',
  expires_at        DATETIME     NULL COMMENT '会话过期时间，过期后可清理分片',
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_upload_user_status (user_id, status),
  KEY idx_upload_expire (status, expires_at),
  KEY idx_upload_hash (user_id, file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大文件上传会话';

CREATE TABLE IF NOT EXISTS file_upload_part (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id  BIGINT       NOT NULL COMMENT '上传会话 file_upload_session.id',
  part_number INT          NOT NULL COMMENT '分片序号，从 1 开始，对应路径参数 part_number',
  part_size   INT          NOT NULL COMMENT '分片字节数',
  part_hash   CHAR(64)     NULL COMMENT '分片 SHA-256，来自请求头 X-Part-Hash',
  bucket_name VARCHAR(64)  NOT NULL COMMENT '分片所在 MinIO 桶',
  object_key  VARCHAR(512) NOT NULL COMMENT '分片对象键',
  status      VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED 已落盘 / FAILED 校验失败',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_upload_part (session_id, part_number),
  KEY idx_upload_part_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='上传分片';
