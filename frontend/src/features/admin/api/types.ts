/**
 * 管理端接口类型：字段全部对齐后端 AdminController / AdminUserVO / SysRole 的 JSON 命名
 */

/** 分页返回结构（后端 PageData） */
export interface PageData<T> {
  list: T[]
  total: number
  page_num: number
  page_size: number
}

/** 后端 AdminUserVO：email 已脱敏，user_id 通过 @JsonProperty 映射为 user_id */
export interface AdminUserVO {
  user_id: number
  username: string
  nickname: string
  email: string
  /** 0=禁用，1=正常 */
  status: number
  /** 角色码，如 SYSTEM_ADMIN / DATA_AUDITOR / STUDENT */
  roles: string[]
}

/** 后端 SysRole 实体（无 @JsonNaming，JSON 为 camelCase） */
export interface AdminRole {
  id: number
  roleCode: string
  roleName: string
  description: string | null
  status: number
  createTime?: string
}

/** 用户列表查询参数 */
export interface AdminUserQuery {
  keyword?: string
  /** 留空表示全部，0=禁用，1=正常 */
  status?: number
  page_num: number
  page_size: number
}

/** 后台登录请求（login_type/account/password/captcha_key/captcha_code 与后端 LoginRequest 一致） */
export interface AdminLoginPayload {
  login_type: 'PASSWORD'
  account: string
  password: string
  captcha_key: string
  captcha_code: string
  /** 后端后续开放管理员专用登录域时透传，当前后端忽略未知字段 */
  scope?: 'ADMIN'
}
