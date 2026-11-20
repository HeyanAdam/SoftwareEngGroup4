import { get, post } from '../../../shared/api/request'
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

/** POST /auth/register 注册（成功 HTTP 201，直接返回 token；幂等键由表单复用） */
export function registerAccount(
  payload: RegisterRequest,
  idempotencyKey: string,
): Promise<RegisterResult> {
  return post<RegisterResult>('/auth/register', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

/** POST /auth/password-reset 重置密码（幂等键由表单复用） */
export function resetPassword(
  payload: PasswordResetRequest,
  idempotencyKey: string,
): Promise<null> {
  return post<null>('/auth/password-reset', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}
