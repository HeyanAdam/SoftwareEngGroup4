import { PERMISSIONS, ROLES } from '../../constants/permissions'
import { BUSINESS_CODE } from '../response'
import type {
  ApiResult,
  CaptchaData,
  CurrentUser,
  LoginByEmailCodeRequest,
  LoginByPasswordRequest,
  LoginResult,
  PasswordResetRequest,
  RegisterRequest,
  RegisterResult,
  VerificationCodeRequest,
  VerificationCodeResult,
} from '../../types/auth'

/** Mock 环境下通用的邮箱验证码 */
export const MOCK_EMAIL_CODE = '123456'

/** 当前有效的图形验证码（每次刷新随机生成，登录时校验） */
let currentCaptchaCode = ''
let currentCaptchaKey = ''

function randomCaptchaCode(): string {
  return String(Math.floor(1000 + Math.random() * 9000))
}

/** 用随机码生成验证码图片（SVG data URI） */
function buildCaptchaImage(code: string): string {
  const offsetX = 22 + Math.floor(Math.random() * 12)
  const waveY = 16 + Math.floor(Math.random() * 12)
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40" viewBox="0 0 120 40">` +
    `<rect width="120" height="40" fill="#eaf7ef"/>` +
    `<path d="M4 30 Q30 8 56 26 T108 20" stroke="#bfe3cd" stroke-width="2" fill="none"/>` +
    `<path d="M6 ${waveY} Q40 34 70 10 T114 28" stroke="#d8eee0" stroke-width="2" fill="none"/>` +
    `<text x="${offsetX}" y="29" font-family="Georgia,serif" font-size="24" font-weight="bold" ` +
    `letter-spacing="4" fill="#3f9c6d">${code}</text>` +
    `</svg>`
  return `data:image/svg+xml;base64,${btoa(svg)}`
}

/** Mock 返回结构：HTTP 状态 + 业务体 + 可选 Retry-After */
export interface MockResponse {
  httpStatus: number
  body: ApiResult<unknown>
  retryAfter?: number
}

function ok<T>(data: T, httpStatus = 200, message = 'success'): MockResponse {
  return { httpStatus, body: { code: BUSINESS_CODE.SUCCESS, message, data } }
}

function fail(httpStatus: number, code: number, message: string, retryAfter?: number): MockResponse {
  return { httpStatus, body: { code, message, data: null }, retryAfter }
}

/** Mock 学生账号（权限码只有 chat:send，与当前库内 STUDENT 一致） */
const MOCK_STUDENT: CurrentUser = {
  user_id: 10001,
  username: 'kaoyan_student',
  nickname: '考研小白',
  email_masked: 'stu***@example.com',
  roles: [ROLES.STUDENT],
  permissions: [PERMISSIONS.CHAT_SEND],
  has_profile: false,
}

/** Mock 管理员账号（拥有全部 20 条权限码） */
const MOCK_ADMIN: CurrentUser = {
  user_id: 9999,
  username: 'admin',
  nickname: '系统管理员',
  email_masked: 'adm***@example.com',
  roles: [ROLES.SYSTEM_ADMIN],
  permissions: Object.values(PERMISSIONS),
  has_profile: true,
}

/** Mock token：用前缀区分身份，刷新页面后仍能还原 */
export const MOCK_TOKEN_ADMIN = 'mock-token-admin'
export const MOCK_TOKEN_STUDENT = 'mock-token-student'

function loginResult(token: string, user: CurrentUser): LoginResult {
  return {
    token,
    token_type: 'Bearer',
    expires_in: 2592000,
    user_id: user.user_id,
    username: user.username,
    nickname: user.nickname,
    roles: user.roles,
    permissions: user.permissions,
    has_profile: user.has_profile,
  }
}

/** GET /auth/captcha */
export function mockCaptcha(): MockResponse {
  currentCaptchaCode = randomCaptchaCode()
  currentCaptchaKey = `mock-captcha-${Date.now()}`
  const data: CaptchaData = {
    captcha_key: currentCaptchaKey,
    image_base64: buildCaptchaImage(currentCaptchaCode),
    expires_in: 300,
  }
  return ok(data)
}

/** POST /auth/verification-codes */
export function mockVerificationCode(payload: VerificationCodeRequest): MockResponse {
  // 三用途互不通用，这里只校验用途取值
  if (!['REGISTER', 'EMAIL_LOGIN', 'RESET_PASSWORD'].includes(payload.purpose)) {
    return fail(400, BUSINESS_CODE.PARAM_ERROR, '验证码用途不正确')
  }
  // 模拟 60 秒内重复发送
  if (payload.email === 'fast@test.com') {
    return fail(400, BUSINESS_CODE.VERIFY_CODE_ERROR, '验证码发送过于频繁，请稍后重试')
  }
  const data: VerificationCodeResult = { expires_in: 300, retry_after: 60 }
  return ok(data)
}

/** POST /auth/login */
export function mockLogin(payload: LoginByPasswordRequest | LoginByEmailCodeRequest): MockResponse {
  if (payload.login_type === 'PASSWORD') {
    // 图形验证码：与最近一次 /auth/captcha 下发的码比对（key 也必须一致）
    if (
      !currentCaptchaCode ||
      payload.captcha_key !== currentCaptchaKey ||
      payload.captcha_code !== currentCaptchaCode
    ) {
      return fail(400, BUSINESS_CODE.VERIFY_CODE_ERROR, '验证码错误或已过期')
    }
    // 验证码校验通过即作废（无论后面密码是否正确）：所以密码错误后必须换一张新的验证码
    currentCaptchaCode = ''
    currentCaptchaKey = ''
    if (payload.account === 'admin' && payload.password === 'Admin@2026') {
      return ok(loginResult(MOCK_TOKEN_ADMIN, MOCK_ADMIN))
    }
    if (payload.account === 'kaoyan_student' && payload.password === 'KaoYan@2026') {
      return ok(loginResult(MOCK_TOKEN_STUDENT, MOCK_STUDENT))
    }
    return fail(401, BUSINESS_CODE.ACCOUNT_PASSWORD_ERROR, '账号或密码错误')
  }

  if (payload.verify_code !== MOCK_EMAIL_CODE) {
    return fail(400, BUSINESS_CODE.VERIFY_CODE_ERROR, '验证码错误或已过期')
  }
  return ok(loginResult(MOCK_TOKEN_STUDENT, MOCK_STUDENT))
}

/** GET /auth/me */
export function mockMe(token: string): MockResponse {
  if (!token) {
    return fail(401, BUSINESS_CODE.NOT_LOGIN, '未登录或登录已失效')
  }
  if (token === MOCK_TOKEN_ADMIN) {
    return ok(MOCK_ADMIN)
  }
  if (token === MOCK_TOKEN_STUDENT || token === 'mock-token-register') {
    return ok(MOCK_STUDENT)
  }
  return fail(401, BUSINESS_CODE.NOT_LOGIN, '未登录或登录已失效')
}

/** POST /auth/logout */
export function mockLogout(): MockResponse {
  return ok(null, 200, '退出成功')
}

/** POST /auth/register（成功返回 HTTP 201，直接带 token） */
export function mockRegister(payload: RegisterRequest): MockResponse {
  if (!payload.agree_agreement) {
    return fail(400, BUSINESS_CODE.PARAM_ERROR, '请先同意用户协议')
  }
  if (payload.username === 'kaoyan_student' || payload.email === 'stu@test.com') {
    return fail(409, BUSINESS_CODE.ACCOUNT_EXISTS, '用户名或邮箱已被注册')
  }
  if (payload.verify_code !== MOCK_EMAIL_CODE) {
    return fail(400, BUSINESS_CODE.VERIFY_CODE_ERROR, '验证码错误或已过期')
  }
  if (payload.password !== payload.confirm_password) {
    return fail(400, BUSINESS_CODE.PASSWORD_NOT_MATCH, '两次输入的密码不一致')
  }
  const data: RegisterResult = {
    user_id: 10002,
    username: payload.username,
    nickname: payload.nickname || payload.username,
    token: 'mock-token-register',
    token_type: 'Bearer',
  }
  return ok(data, 201, '注册成功')
}

/** POST /auth/password-reset */
export function mockPasswordReset(payload: PasswordResetRequest): MockResponse {
  if (payload.verify_code !== MOCK_EMAIL_CODE) {
    return fail(400, BUSINESS_CODE.VERIFY_CODE_ERROR, '验证码错误或已过期')
  }
  if (payload.new_password !== payload.confirm_password) {
    return fail(400, BUSINESS_CODE.PASSWORD_NOT_MATCH, '两次输入的密码不一致')
  }
  return ok(null, 200, '密码已重置，请重新登录')
}
