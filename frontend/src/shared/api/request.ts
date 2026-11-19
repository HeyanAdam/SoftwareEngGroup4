import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { BUSINESS_CODE, businessMessage, NETWORK_ERROR_CODE } from './response'
import { dispatchMock } from './mock'
import type { MockResponse } from './mock/authMock'
import type { ApiResult } from '../types/auth'

/** 本地存储里 token 的 key（与 P1 约定一致） */
export const TOKEN_STORAGE_KEY = 'sa_token'

/** 401 时派发的全局事件，路由层监听后跳登录页 */
export const UNAUTHORIZED_EVENT = 'auth:unauthorized'

/** 普通业务接口前缀（auth / chat / user / knowledge 等） */
export const API_BASE_URL = '/api/v1'

/**
 * 管理端接口前缀。
 * 后端 AdminController 的路由是 /api/admin/**，不是 /api/v1/admin，
 * 所以这里必须单独一个实例，避免拼成 /api/v1/api/admin/users 造成 404。
 */
export const ADMIN_API_BASE_URL = '/api/admin'

/** 统一错误对象：业务码 + HTTP 状态 + 重试等待 + 请求 ID */
export class ApiError extends Error {
  code: number
  httpStatus: number
  retryAfter: number
  requestId: string

  constructor(options: {
    message: string
    code: number
    httpStatus: number
    retryAfter?: number
    requestId?: string
  }) {
    super(options.message)
    this.name = 'ApiError'
    this.code = options.code
    this.httpStatus = options.httpStatus
    this.retryAfter = options.retryAfter ?? 0
    this.requestId = options.requestId ?? ''
  }
}

function createRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 生成幂等键，注册 / 重置密码提交时使用 */
export function createIdempotencyKey(): string {
  return createRequestId()
}

function readHeader(headers: unknown, name: string): unknown {
  if (!headers || typeof headers !== 'object') return undefined
  const record = headers as Record<string, unknown>
  return record[name]
}

function parseRetryAfter(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return Math.floor(value)
  }
  if (typeof value === 'string') {
    const seconds = Number(value)
    if (Number.isFinite(seconds) && seconds > 0) {
      return Math.floor(seconds)
    }
  }
  return 0
}

/** 创建带统一拦截器的 axios 实例（普通接口与管理接口共用同一套逻辑） */
function createService(baseURL: string): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 15000,
  })

  // 请求拦截：自动带 Token 与请求 ID
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY)
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    if (!config.headers.get('X-Request-Id')) {
      config.headers.set('X-Request-Id', createRequestId())
    }
    return config
  })

  // 响应拦截：统一解析 {code,message,data}，统一抛 ApiError
  instance.interceptors.response.use(
    (response: AxiosResponse<ApiResult<unknown>>) => {
      const body = response.data
      if (
        body &&
        typeof body === 'object' &&
        typeof body.code === 'number' &&
        body.code !== BUSINESS_CODE.SUCCESS
      ) {
        return Promise.reject(
          new ApiError({
            message: body.message || businessMessage(body.code) || '请求失败',
            code: body.code,
            httpStatus: response.status,
            requestId: String(readHeader(response.headers, 'x-request-id') ?? ''),
          }),
        )
      }
      return response
    },
    (error: unknown) => {
      const axiosError = error as AxiosError<ApiResult<unknown>>
      const response = axiosError.response

      if (!response) {
        return Promise.reject(
          new ApiError({
            message: '网络异常，请检查网络后重试',
            code: NETWORK_ERROR_CODE,
            httpStatus: 0,
          }),
        )
      }

      const status = response.status
      const body = response.data
      const code =
        body && typeof body === 'object' && typeof body.code === 'number' ? body.code : status
      const message =
        (body && typeof body === 'object' && body.message) ||
        businessMessage(code) ||
        `请求失败（HTTP ${status}）`
      const retryAfter = parseRetryAfter(readHeader(response.headers, 'retry-after'))
      const requestId = String(readHeader(response.headers, 'x-request-id') ?? '')

      // 仅「未登录 / Token 失效」才清登录态：
      // 业务码 10001，或裸 401（响应体里没有业务码）。账号或密码错误（10002）同样返回 401，但必须保留登录态。
      const hasBusinessCode = body && typeof body === 'object' && typeof body.code === 'number'
      const isTokenInvalid = code === BUSINESS_CODE.NOT_LOGIN || (status === 401 && !hasBusinessCode)

      if (isTokenInvalid) {
        localStorage.removeItem(TOKEN_STORAGE_KEY)
        window.dispatchEvent(
          new CustomEvent(UNAUTHORIZED_EVENT, { detail: { code, message, requestId } }),
        )
      }

      return Promise.reject(
        new ApiError({ message, code, httpStatus: status, retryAfter, requestId }),
      )
    },
  )

  return instance
}

/** 普通业务接口实例（/api/v1） */
const service = createService(API_BASE_URL)

/** 管理端接口实例（/api/admin）：用户、角色、向量库等后台接口统一用它 */
export const adminRequest = createService(ADMIN_API_BASE_URL)

/** 把 Mock 响应转成和后端一致的返回值 / ApiError */
function resolveMock<T>(mock: MockResponse): T {
  const { body, httpStatus, retryAfter } = mock
  if (body.code !== BUSINESS_CODE.SUCCESS) {
    throw new ApiError({
      message: body.message || businessMessage(body.code) || '请求失败',
      code: body.code,
      httpStatus,
      retryAfter: retryAfter ?? 0,
    })
  }
  return body.data as T
}

/**
 * 统一执行：Mock 开启且命中则直接返回模拟数据，否则走真实 axios
 * @param send 真实请求的发送函数
 */
async function execute<T>(
  method: string,
  url: string,
  payload: unknown,
  send: () => Promise<AxiosResponse<ApiResult<T>>>,
): Promise<T> {
  const mock = dispatchMock(method, url, payload, localStorage.getItem(TOKEN_STORAGE_KEY) ?? '')
  if (mock) {
    const result = await mock
    return resolveMock<T>(result)
  }
  const response = await send()
  return response.data.data
}

/** GET，返回业务 data */
export async function get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return execute<T>('GET', url, undefined, () => service.get<ApiResult<T>>(url, config))
}

/** POST，返回业务 data */
export async function post<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('POST', url, data, () => service.post<ApiResult<T>>(url, data, config))
}

/** PUT，返回业务 data */
export async function put<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('PUT', url, data, () => service.put<ApiResult<T>>(url, data, config))
}

/** PATCH，返回业务 data */
export async function patch<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('PATCH', url, data, () => service.patch<ApiResult<T>>(url, data, config))
}

/** DELETE，返回业务 data */
export async function del<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return execute<T>('DELETE', url, undefined, () => service.delete<ApiResult<T>>(url, config))
}

/** 文件上传 */
export async function upload<T = unknown>(
  url: string,
  formData: FormData,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await service.post<ApiResult<T>>(url, formData, {
    ...config,
    headers: { 'Content-Type': 'multipart/form-data', ...(config?.headers ?? {}) },
  })
  return response.data.data
}

/** 管理端 GET（自动走 /api/admin 前缀） */
export async function adminGet<T = unknown>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('GET', url, undefined, () => adminRequest.get<ApiResult<T>>(url, config))
}

/** 管理端 POST（自动走 /api/admin 前缀） */
export async function adminPost<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('POST', url, data, () => adminRequest.post<ApiResult<T>>(url, data, config))
}

/** 管理端 PUT（自动走 /api/admin 前缀） */
export async function adminPut<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('PUT', url, data, () => adminRequest.put<ApiResult<T>>(url, data, config))
}

/** 管理端 DELETE（自动走 /api/admin 前缀） */
export async function adminDel<T = unknown>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  return execute<T>('DELETE', url, undefined, () => adminRequest.delete<ApiResult<T>>(url, config))
}

/** 兼容既有代码的具名导出（早期 FOUND-04 写法使用） */
export const request = service

export default service
