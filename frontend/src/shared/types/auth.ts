/**
 * 认证相关类型定义
 * 字段命名全部对齐后端接口（snake_case），依据《P1交付给P3_前端登录联调说明》
 */

/** 接口统一返回结构 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 图形验证码 */
export interface CaptchaData {
  captcha_key: string
  image_base64: string
  expires_in: number
}

/** 邮箱验证码用途 */
export type VerificationPurpose = 'REGISTER' | 'EMAIL_LOGIN' | 'RESET_PASSWORD'

/** 发送邮箱验证码请求 */
export interface VerificationCodeRequest {
  email: string
  purpose: VerificationPurpose
}

/** 发送邮箱验证码结果 */
export interface VerificationCodeResult {
  expires_in: number
  retry_after: number
}

/** 登录方式 */
export type LoginType = 'PASSWORD' | 'EMAIL_CODE'

/** 账号密码登录请求 */
export interface LoginByPasswordRequest {
  login_type: 'PASSWORD'
  account: string
  password: string
  captcha_key: string
  captcha_code: string
}

/** 邮箱验证码登录请求 */
export interface LoginByEmailCodeRequest {
  login_type: 'EMAIL_CODE'
  email: string
  verify_code: string
}

export type LoginRequest = LoginByPasswordRequest | LoginByEmailCodeRequest

/** 登录成功返回（含权限快照） */
export interface LoginResult {
  token: string
  token_type: string
  expires_in: number
  user_id: number
  username: string
  nickname: string
  roles: string[]
  permissions: string[]
  has_profile: boolean
}

/** GET /auth/me 返回 */
export interface CurrentUser {
  user_id: number
  username: string
  nickname: string
  email_masked: string
  roles: string[]
  permissions: string[]
  has_profile: boolean
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  email: string
  verify_code: string
  password: string
  confirm_password: string
  nickname?: string
  agree_agreement: boolean
}

/** 注册结果（HTTP 201） */
export interface RegisterResult {
  user_id: number
  username: string
  nickname: string
  token: string
  token_type: string
}

/** 重置密码请求 */
export interface PasswordResetRequest {
  email: string
  verify_code: string
  new_password: string
  confirm_password: string
}
