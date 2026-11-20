-- V8：为学生角色补齐文件域权限（对齐《接口设计文档 V1.1 适配版》10.1 节）
-- 背景：V6 种子只给学生 chat:send；文件接口按文档需要 file:upload / file:view / file:download / file:delete，
--       否则学生上传头像、学习资料会直接 10008。
-- 幂等：INSERT IGNORE + 先 SELECT 定位，重复执行不产生重复绑定。
-- 只动 STUDENT 这一个角色，SYSTEM_ADMIN 由 RbacInitializer 启动时自动补齐全部权限码。

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
  ON p.perm_code IN ('file:view', 'file:upload', 'file:download', 'file:delete')
WHERE r.role_code = 'STUDENT';
