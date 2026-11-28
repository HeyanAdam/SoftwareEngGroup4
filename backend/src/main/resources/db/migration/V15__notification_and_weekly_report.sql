-- ============================================================
-- V15: 站内通知与每周学习诊断
-- 后端 B 创建，不修改 V13/V14
-- ============================================================

CREATE TABLE IF NOT EXISTS user_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
  title VARCHAR(200) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  action_path VARCHAR(500) NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  read_at DATETIME NULL,
  dedup_key VARCHAR(191) NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_notification_dedup (dedup_key),
  KEY idx_notification_user_read_time (user_id, is_read, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户站内通知';

CREATE TABLE IF NOT EXISTS study_weekly_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  week_start DATE NOT NULL,
  week_end DATE NOT NULL,
  completion_rate DECIMAL(6,4) NOT NULL DEFAULT 0,
  study_minutes INT NOT NULL DEFAULT 0,
  chat_questions INT NOT NULL DEFAULT 0,
  weak_subjects_json JSON NULL,
  next_actions_json JSON NULL,
  summary VARCHAR(2000) NOT NULL DEFAULT '',
  generated_at DATETIME NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_report_user_week (user_id, week_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每周学习诊断';
