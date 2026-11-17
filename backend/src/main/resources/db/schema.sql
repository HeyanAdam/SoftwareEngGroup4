-- AI 考研 RAG 系统建表脚本（MySQL 8.0）
CREATE DATABASE IF NOT EXISTS kaoyan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE kaoyan;

-- ============ RBAC ============
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录名',
  password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  nickname VARCHAR(64),
  email VARCHAR(128),
  avatar VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL UNIQUE,
  role_name VARCHAR(64) NOT NULL,
  description VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '角色';

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  perm_code VARCHAR(128) NOT NULL UNIQUE COMMENT '权限标识',
  perm_name VARCHAR(64) NOT NULL,
  perm_type VARCHAR(16) NOT NULL DEFAULT 'API' COMMENT 'MENU/BUTTON/API',
  parent_id BIGINT DEFAULT 0,
  path VARCHAR(255),
  sort INT DEFAULT 0
) COMMENT '权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id)
) COMMENT '用户-角色';

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_perm (role_id, permission_id)
) COMMENT '角色-权限';

-- ============ 知识库 / 文档 ============
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500),
  user_id BIGINT NOT NULL,
  visibility TINYINT NOT NULL DEFAULT 0 COMMENT '0私有 1公开',
  doc_count INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_kb_user (user_id)
) COMMENT '知识库';

CREATE TABLE IF NOT EXISTS kb_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  kb_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  file_ext VARCHAR(16),
  file_size BIGINT DEFAULT 0,
  object_key VARCHAR(255) COMMENT 'MinIO 对象路径',
  raw_text LONGTEXT COMMENT '解析后的全文，落库持久化',
  content_hash VARCHAR(64) COMMENT '全文哈希，用于判断重复',
  chunk_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
  error_msg VARCHAR(1000),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_doc_kb (kb_id),
  KEY idx_doc_user (user_id)
) COMMENT '文档（含原文全文）';

CREATE TABLE IF NOT EXISTS kb_document_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  kb_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  milvus_id BIGINT COMMENT 'Milvus 主键，等于本表 id',
  char_count INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chunk_doc (doc_id),
  KEY idx_chunk_kb (kb_id)
) COMMENT '文档切片';

-- ============ 会话 ============
CREATE TABLE IF NOT EXISTS chat_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(255),
  kb_id BIGINT COMMENT '限定知识库，可为空',
  deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_conv_user (user_id)
) COMMENT '会话';

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL COMMENT 'user/assistant/system',
  content LONGTEXT NOT NULL,
  references_json TEXT COMMENT '引用片段 JSON',
  model_name VARCHAR(128),
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_msg_conv (conversation_id)
) COMMENT '会话消息';
