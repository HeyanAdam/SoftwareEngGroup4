-- ============================================================
-- V13: 学习计划、学习任务、用户推荐三张表
-- 后端 A 创建,不要修改 V1~V12; 后端 B 的 V15 不改动 V13
-- ============================================================

-- 每日学习计划 (一人一天一条)
CREATE TABLE IF NOT EXISTS study_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  plan_date DATE NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_plan_user_date (user_id, plan_date),
  KEY idx_plan_user_status (user_id, status, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日学习计划';

-- 学习任务 (属于某计划,乐观锁,逾期由服务端计算不存 DB)
CREATE TABLE IF NOT EXISTS study_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  plan_date DATE NOT NULL,
  title VARCHAR(200) NOT NULL,
  subject VARCHAR(64) NOT NULL,
  knowledge_point VARCHAR(200) NULL,
  estimated_minutes INT NOT NULL DEFAULT 0,
  due_at DATETIME NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'TODO',
  priority INT NOT NULL DEFAULT 50,
  version INT NOT NULL DEFAULT 0,
  completed_at DATETIME NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  KEY idx_task_user_date (user_id, plan_date, deleted),
  KEY idx_task_due (user_id, status, due_at, deleted),
  KEY idx_task_plan (plan_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习任务';

-- 用户推荐 (去重键 UNIQUE INDEX 兜底)
CREATE TABLE IF NOT EXISTS user_recommendation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  scene VARCHAR(32) NOT NULL,
  context_type VARCHAR(32) NOT NULL,
  context_id BIGINT NULL,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(200) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  action_label VARCHAR(64) NOT NULL,
  action_path VARCHAR(500) NULL,
  score DECIMAL(6,4) NOT NULL DEFAULT 0,
  estimated_minutes INT NULL,
  subject VARCHAR(64) NULL,
  knowledge_point VARCHAR(200) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  accepted_task_id BIGINT NULL,
  dedup_key VARCHAR(191) NOT NULL,
  expires_at DATETIME NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_recommendation_dedup (dedup_key),
  KEY idx_recommendation_user_scene (user_id, scene, status, expires_at),
  KEY idx_recommendation_context (user_id, context_type, context_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推荐';
