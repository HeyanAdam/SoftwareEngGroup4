-- V10：补种文件域权限码，并真正把文件权限绑定给学生角色。
-- 背景：V6 的权限种子只种了 kb:* / doc:* / chat:send / admin:*，没有 file:view / file:upload /
--       file:download / file:delete 这四个码；V8 用 JOIN sys_permission 去绑定，字典里当时不存在这四条，
--       JOIN 匹配 0 行，迁移被记成「执行成功」但实际插入 0 条。结果 STUDENT 一直没有 file:* 权限：
--       前端 /files 路由守卫判定无 file:view/file:upload 直接 403，上传/下载接口返回 10008。
-- 处理：先补种四个权限码（已存在则按 code 覆盖名称/排序，可重复执行），再 INSERT IGNORE 绑定 STUDENT。
-- 幂等：sys_permission.perm_code 唯一，sys_role_permission 有 uk_role_perm(role_id, permission_id) 唯一键，
--       两条语句重复执行都不会产生重复权限码或重复绑定。

INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, path, sort)
VALUES
  ('file:view', '查看文件', 'API', 0, NULL, 20),
  ('file:upload', '上传文件', 'API', 0, NULL, 21),
  ('file:download', '下载文件', 'API', 0, NULL, 22),
  ('file:delete', '删除文件', 'API', 0, NULL, 23)
AS seed
ON DUPLICATE KEY UPDATE
  perm_name = seed.perm_name,
  perm_type = seed.perm_type,
  parent_id = seed.parent_id,
  path = seed.path,
  sort = seed.sort;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
  ON p.perm_code IN ('file:view', 'file:upload', 'file:download', 'file:delete')
WHERE r.role_code = 'STUDENT';
