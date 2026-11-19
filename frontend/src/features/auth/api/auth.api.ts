import { createIdempotencyKey, get, post } from '../../../shared/api/request'
import type {
  CaptchaData,
  LoginByEmailCodeRequest,
  LoginByPasswordRequest,
  LoginResult,
  PasswordResetRequest,
  RegisterRequest,
  RegisterResult,
  VerificationCodeRequest,
  VerificationCodeResult,
} from '../../../shared/types/auth'

/** GET /auth/captcha 图形验证码 */
export function fetchCaptcha(): Promise<CaptchaData> {
  return get<CaptchaData>('/auth/captcha')
}

/** POST /auth/verification-codes 发送邮箱验证码 */
export function sendVerificationCode(
  payload: VerificationCodeRequest,
): Promise<VerificationCodeResult> {
  return post<VerificationCodeResult>('/auth/verification-codes', payload)
}

/** POST /auth/login 账号密码登录 */
export function loginWithPassword(payload: LoginByPasswordRequest): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload)
}

/** POST /auth/login 邮箱验证码登录 */
export function loginWithEmailCode(payload: LoginByEmailCodeRequest): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload)
}

/** POST /auth/register 注册（成功 HTTP 201，直接返回 token） */
export function registerAccount(payload: RegisterRequest): Promise<RegisterResult> {
  return post<RegisterResult>('/auth/register', payload, {
    headers: { 'Idempotency-Key': createIdempotencyKey() },
  })
}

/** POST /auth/password-reset 重置密码 */
export function resetPassword(payload: PasswordResetRequest): Promise<null> {
  return post<null>('/auth/password-reset', payload, {
    headers: { 'Idempotency-Key': createIdempotencyKey() },
  })
}
