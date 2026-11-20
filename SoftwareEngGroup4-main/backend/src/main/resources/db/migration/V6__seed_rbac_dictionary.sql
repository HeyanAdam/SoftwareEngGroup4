-- V6：RBAC 角色、权限与角色权限关系种子。
-- 这是 RBAC 字典的唯一写入来源；Java 启动器只校验，不新增或改写数据。
-- 不预置管理员账号，第一个注册用户仍由 AuthService 绑定 SYSTEM_ADMIN。

INSERT INTO sys_role (role_code, role_name, description, status)
VALUES
  ('STUDENT', '学生', '默认注册角色，只能问答', 1),
  ('CONTENT_OPERATOR', '内容运营', '维护知识库、文档和向量索引', 1),
  ('DATA_AUDITOR', '数据审计员', '只读查看用户、角色和知识库概览', 1),
  ('SYSTEM_ADMIN', '系统管理员', '拥有全部系统权限', 1)
AS seed
ON DUPLICATE KEY UPDATE
  role_name = seed.role_name,
  description = seed.description,
  status = seed.status;

INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, path, sort)
VALUES
  ('kb:view', '查看知识库', 'API', 0, NULL, 10),
  ('kb:create', '创建知识库', 'API', 0, NULL, 20),
  ('kb:delete', '删除知识库', 'API', 0, NULL, 30),
  ('doc:upload', '上传文档', 'API', 0, NULL, 40),
  ('doc:delete', '删除文档', 'API', 0, NULL, 50),
  ('chat:send', '智能问答', 'API', 0, NULL, 60),
  ('admin:user:list', '用户列表', 'API', 0, NULL, 70),
  ('admin:user:update', '用户管理', 'API', 0, NULL, 80),
  ('admin:role:list', '角色列表', 'API', 0, NULL, 90),
  ('admin:role:update', '角色权限管理', 'API', 0, NULL, 100),
  ('admin:vector:manage', '向量库管理', 'API', 0, NULL, 110)
AS seed
ON DUPLICATE KEY UPDATE
  perm_name = seed.perm_name,
  perm_type = seed.perm_type,
  parent_id = seed.parent_id,
  path = seed.path,
  sort = seed.sort;

-- 兼容已由旧 RbacInitializer 初始化的数据库：
-- 先把旧 ADMIN 的用户绑定和自定义角色权限迁移到 SYSTEM_ADMIN，再移除旧角色。
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT legacy_user_role.user_id, system_admin.id
FROM sys_user_role legacy_user_role
JOIN sys_role legacy_role ON legacy_role.id = legacy_user_role.role_id
JOIN sys_role system_admin ON system_admin.role_code = 'SYSTEM_ADMIN'
WHERE legacy_role.role_code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT system_admin.id, legacy_role_permission.permission_id
FROM sys_role legacy_role
JOIN sys_role_permission legacy_role_permission
  ON legacy_role_permission.role_id = legacy_role.id
JOIN sys_role system_admin ON system_admin.role_code = 'SYSTEM_ADMIN'
WHERE legacy_role.role_code = 'ADMIN';

DELETE legacy_role_permission
FROM sys_role_permission legacy_role_permission
JOIN sys_role legacy_role ON legacy_role.id = legacy_role_permission.role_id
WHERE legacy_role.role_code = 'ADMIN';

DELETE legacy_user_role
FROM sys_user_role legacy_user_role
JOIN sys_role legacy_role ON legacy_role.id = legacy_user_role.role_id
WHERE legacy_role.role_code = 'ADMIN';

DELETE FROM sys_role WHERE role_code = 'ADMIN';

-- SYSTEM_ADMIN：全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission
WHERE role.role_code = 'SYSTEM_ADMIN';

-- STUDENT：只能使用问答
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission
WHERE role.role_code = 'STUDENT'
  AND permission.perm_code IN ('chat:send');

-- CONTENT_OPERATOR：知识库、文档与向量维护
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission
WHERE role.role_code = 'CONTENT_OPERATOR'
  AND permission.perm_code IN (
    'kb:view', 'kb:create', 'kb:delete',
    'doc:upload', 'doc:delete', 'admin:vector:manage'
  );

-- DATA_AUDITOR：只读查看现有用户、角色和知识库权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission
WHERE role.role_code = 'DATA_AUDITOR'
  AND permission.perm_code IN ('kb:view', 'admin:user:list', 'admin:role:list');
