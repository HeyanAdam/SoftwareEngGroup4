-- ============================================================
-- V14: 推荐 + 通知权限字典 + STUDENT 角色绑定
-- 后端 A 统一维护,后端 B 不直接改此文件
-- ============================================================

-- 权限字典: 只在不存在时插入,已存在则按 ON DUPLICATE KEY UPDATE 幂等修正元数据
INSERT INTO sys_permission
  (perm_code, perm_name, perm_type, parent_id, path, sort)
VALUES
  ('recommendation:view', '查看导学推荐', 'API', 0, NULL, 45),
  ('recommendation:update', '接受或忽略推荐', 'API', 0, NULL, 46),
  ('notification:view', '查看通知', 'API', 0, NULL, 47),
  ('notification:update', '更新通知状态', 'API', 0, NULL, 48)
AS seed
ON DUPLICATE KEY UPDATE
  perm_name = seed.perm_name,
  perm_type = seed.perm_type,
  parent_id = seed.parent_id,
  path = seed.path,
  sort = seed.sort;

-- STUDENT 角色绑定这 4 条权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
  ON p.perm_code IN (
    'recommendation:view',
    'recommendation:update',
    'notification:view',
    'notification:update'
  )
WHERE r.role_code = 'STUDENT';
