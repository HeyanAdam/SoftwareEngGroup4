/**
 * 角色与权限码常量
 * 来源：《权限表.md》（P2 交付，20 条权限码 / 4 个角色码）
 * 规则：前端只允许用权限码判断显隐，禁止按角色名硬编码控制界面
 */

/** 角色码（后端 sys_role.role_code，全大写） */
export const ROLES = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  CONTENT_OPERATOR: 'CONTENT_OPERATOR',
  DATA_AUDITOR: 'DATA_AUDITOR',
  STUDENT: 'STUDENT',
} as const

export type RoleCode = (typeof ROLES)[keyof typeof ROLES]

/** 权限码（20 条，格式 module:action） */
export const PERMISSIONS = {
  STUDY_PLAN_VIEW: 'study-plan:view',
  STUDY_PLAN_CREATE: 'study-plan:create',
  STUDY_PLAN_UPDATE: 'study-plan:update',
  STUDY_TASK_UPDATE: 'study-task:update',
  QUESTION_VIEW: 'question:view',
  FILE_VIEW: 'file:view',
  FILE_UPLOAD: 'file:upload',
  FILE_DOWNLOAD: 'file:download',
  FILE_DELETE: 'file:delete',
  KB_VIEW: 'kb:view',
  KB_CREATE: 'kb:create',
  KB_DELETE: 'kb:delete',
  DOC_UPLOAD: 'doc:upload',
  DOC_DELETE: 'doc:delete',
  CHAT_SEND: 'chat:send',
  ADMIN_USER_LIST: 'admin:user:list',
  ADMIN_USER_UPDATE: 'admin:user:update',
  ADMIN_ROLE_LIST: 'admin:role:list',
  ADMIN_ROLE_UPDATE: 'admin:role:update',
  ADMIN_VECTOR_MANAGE: 'admin:vector:manage',
} as const

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS]
