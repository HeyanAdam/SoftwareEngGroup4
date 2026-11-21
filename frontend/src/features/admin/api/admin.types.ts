/**
 * 角色权限、权限字典与登录日志的接口类型。
 *
 * 字段名对齐后端实现（AdminController / SysRole / SysPermission / LoginLog），
 * 不使用文档里的示意命名，避免联调时出现「前端 ip、后端 ipAddress」这类错位。
 *
 * 角色实体复用甲在 ./types.ts 里定义的 AdminRole（同属后端 SysRole），
 * 这里只做别名与新类型，不重复声明，防止两处定义漂移。
 */
import type { AdminRole } from './types'

/** 后端 SysRole 实体（id / roleCode / roleName / description / status） */
export type SysRole = AdminRole

/** 角色列表行：角色实体 + 汇总统计 */
export interface AdminRoleSummary extends AdminRole {
  /** 已授权限数（SYSTEM_ADMIN 为全量 20） */
  permissionCount: number
  /**
   * 绑定该角色的用户数。
   * 后端尚未提供 `/admin/roles/summary` 时无法统计，前端用 null 表示「未知」，
   * 不伪造 0，也不为每个角色额外翻页查用户。
   */
  userCount: number | null
}

/** 后端 SysPermission：权限字典一行 */
export interface SysPermission {
  id: number
  /** 权限码，如 study-plan:view / admin:role:update */
  permCode: string
  /** 中文名，如「查看学习计划」 */
  permName: string
  /** MENU / BUTTON / API */
  permType: string
  parentId: number
  /** 后端可能为 null（当前种子均为 null） */
  path?: string | null
  /** 后端排序号（历史数据存在交叉/旧排序，仅用于同模块内展示顺序，不用于分组） */
  sort: number
}

/** 后端 LoginLog：登录日志一行（只增不改） */
export interface AdminLoginLog {
  id: number
  userId?: number | null
  account: string
  /** 1 成功 / 0 失败（后端是 Integer，不是 boolean） */
  success: number
  ipAddress: string
  userAgent: string
  /** 失败原因，成功记录为 null */
  failureReason?: string | null
  /** 后端 LocalDateTime，JSON 形如 2026-11-21T10:20:30 */
  createTime: string
}

/** 新建角色请求体 */
export interface RoleCreatePayload {
  roleCode: string
  roleName: string
  description: string
}

/** 编辑角色请求体：只允许改名称与描述，roleCode 不可改 */
export interface RoleUpdatePayload {
  roleName: string
  description: string
}

/** 登录日志查询参数 */
export interface AdminLoginLogQuery {
  account?: string
  limit?: number
}
