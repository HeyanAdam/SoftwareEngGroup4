import type { LoginByEmailCodeRequest, LoginByPasswordRequest, PasswordResetRequest, RegisterRequest, VerificationCodeRequest } from '../../types/auth'
import {
  mockCaptcha,
  mockLogin,
  mockLogout,
  mockMe,
  mockPasswordReset,
  mockRegister,
  mockVerificationCode,
  type MockResponse,
} from './authMock'

/** 是否启用 Mock（.env.development 中 VITE_USE_MOCK=true） */
export const isMockEnabled = import.meta.env.VITE_USE_MOCK === 'true'

/** 模拟网络延迟，让页面的 loading 状态能看见 */
const MOCK_DELAY = 120

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

interface MockRoute {
  method: string
  url: string
  handle: (payload: unknown, token: string) => MockResponse
}

const routes: MockRoute[] = [
  { method: 'GET', url: '/auth/captcha', handle: () => mockCaptcha() },
  {
    method: 'POST',
    url: '/auth/verification-codes',
    handle: (payload) => mockVerificationCode(payload as VerificationCodeRequest),
  },
  {
    method: 'POST',
    url: '/auth/login',
    handle: (payload) =>
      mockLogin(payload as LoginByPasswordRequest | LoginByEmailCodeRequest),
  },
  { method: 'GET', url: '/auth/me', handle: (_payload, token) => mockMe(token) },
  { method: 'POST', url: '/auth/logout', handle: () => mockLogout() },
  {
    method: 'POST',
    url: '/auth/register',
    handle: (payload) => mockRegister(payload as RegisterRequest),
  },
  {
    method: 'POST',
    url: '/auth/password-reset',
    handle: (payload) => mockPasswordReset(payload as PasswordResetRequest),
  },
]

/**
 * 分发 Mock 请求
 * @returns 命中则返回 Mock 响应，未命中返回 null（放行真实 axios 请求）
 */
export function dispatchMock(
  method: string,
  url: string,
  payload: unknown,
  token: string,
): Promise<MockResponse> | null {
  if (!isMockEnabled) return null

  const normalizedUrl = url.split('?')[0].replace(/\/$/, '')
  const upperMethod = method.toUpperCase()
  const route = routes.find((item) => item.method === upperMethod && item.url === normalizedUrl)
  if (!route) return null

  return delay(MOCK_DELAY).then(() => route.handle(payload, token))
}
