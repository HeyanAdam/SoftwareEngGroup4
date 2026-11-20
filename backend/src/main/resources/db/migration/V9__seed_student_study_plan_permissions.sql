-- V9：为学生角色补齐督学域权限（学习计划 / 学习任务）
-- 背景：前端 /study-plans 路由的 meta.permission 要求 study-plan:view 或 study-plan:create，
--       后端接口同样按 study-plan:view / study-plan:create / study-plan:update / study-task:update 鉴权；
--       但 STUDENT 角色自 V6/V8 起只授了 chat:send 与 4 条 file:*，
--       结果学生点「学习计划」被路由守卫拦成 403，看不到自己的计划。
-- 幂等：INSERT IGNORE + 先 SELECT 定位，重复执行不产生重复绑定。
-- 只动 STUDENT 这一个角色，SYSTEM_ADMIN 由 RbacInitializer 启动时自动补齐全部权限码。

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
  ON p.perm_code IN ('study-plan:view', 'study-plan:create', 'study-plan:update', 'study-task:update')
WHERE r.role_code = 'STUDENT';
