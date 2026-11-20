/**
 * B：文件上传专用的 HTTP 客户端（C 完成正式登录态之前的临时接入层）
 *
 * 约定（群内固定，2026-11 版）：
 * - 文件接口统一前缀 `/api/v1/files`
 * - 请求头统一 `Authorization: Bearer <token>`
 * - 临时 Token 存储键 `kaoyan_access_token`（兼容旧键 `satoken` / `sa_token`）
 * - 功能先合到集成分支 `integration/file-storage-final`
 *
 * 正式切换点（由 D 在集成 PR 里做）：
 * 1. `file.api.ts` 改用 `shared/api/request.ts`
 * 2. 删除对 `fileClient` 的引用
 * 3. 删除本文件
 * 4. 由 C 的 authSession 注入 Token
 * 5. 重新跑上传与 401 用例
 */

import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

/**
 * 后端统一响应体（`com.group4.kaoyan.common.R`）。
 * 这里只声明 B 需要的字段，不依赖 C 的 `shared/types/auth`，避免两模块耦合。
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 文件接口前缀，与后端 FileController @RequestMapping("/api/v1/files") 一致 */
export const FILE_API_BASE_URL = '/api/v1'

/** 临时 Token 存储键（《B 指南》§4.1 约定） */
export const FILE_TOKEN_KEY = 'kaoyan_access_token'

/**
 * 兼容键：集成分支上 C 的登录页 / 后端 Sa-Token 现存的键名。
 * 读取顺序为 `kaoyan_access_token` → `satoken` → `sa_token`；
 * 正式切到 `shared/api/request.ts` 后本文件整体删除。
 */
export const LEGACY_TOKEN_KEYS = ['satoken', 'sa_token'] as const

/** 401（Token 失效）全局事件，页面监听后引导重新登录 */
export const FILE_UNAUTHORIZED_EVENT = 'file:unauthorized'

/** 403（无权限）全局事件 */
export const FILE_FORBIDDEN_EVENT = 'file:forbidden'

/** 分片请求超时下限：60 秒 */
export const FILE_REQUEST_TIMEOUT = 60000

/** 需要自动重试的 HTTP 状态 */
const RETRIABLE_HTTP_STATUS = new Set([502, 503, 504])

/** 统一错误对象：业务码 + HTTP 状态 + 是否可重试 */
export class FileApiError extends Error {
  code: number
  httpStatus: number
  /** 网络超时 / 连接中断 / 502 503 504 时可重试，其余直接失败 */
  retriable: boolean

  constructor(options: {
    message: string
    code?: number
    httpStatus?: number
    retriable?: boolean
  }) {
    super(options.message)
    this.name = 'FileApiError'
    this.code = options.code ?? -1
    this.httpStatus = options.httpStatus ?? 0
    this.retriable = options.retriable ?? false
  }
}

/** 读取临时 Token */
export function readFileToken(): string {
  const primary = localStorage.getItem(FILE_TOKEN_KEY)
  if (primary) return primary
  for (const key of LEGACY_TOKEN_KEYS) {
    const legacy = localStorage.getItem(key)
    if (legacy) return legacy
  }
  return ''
}

/** 写入临时 Token（联调时也可直接 localStorage.setItem('kaoyan_access_token', '<token>')） */
export function saveFileToken(token: string): void {
  localStorage.setItem(FILE_TOKEN_KEY, token)
}

/** 清理临时 Token（401 时调用） */
export function clearFileToken(): void {
  localStorage.removeItem(FILE_TOKEN_KEY)
  for (const key of LEGACY_TOKEN_KEYS) localStorage.removeItem(key)
}

function createRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `file-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 生成幂等键，初始化分片会话时使用；同一次逻辑操作重试复用同一个键 */
export function createIdempotencyKey(): string {
  return createRequestId()
}

function errorMessageOf(error: AxiosError<ApiResult<unknown>>): string {
  const body = error.response?.data
  if (body && typeof body === 'object' && body.message) {
    return body.message
  }
  return error.message || '请求失败'
}

/** axios 错误 -> FileApiError，并处理 401 / 403 */
function toFileApiError(error: unknown): FileApiError {
  if (error instanceof FileApiError) return error
  if (!axios.isAxiosError(error)) {
    return new FileApiError({ message: (error as Error)?.message ?? '请求失败' })
  }

  const axiosError = error as AxiosError<ApiResult<unknown>>
  const response = axiosError.response
  const status = response?.status ?? 0

  // 无响应：网络中断 / 超时 / 主动取消
  if (!response) {
    const isAbort = axiosError.code === 'ERR_CANCELED'
    return new FileApiError({
      message: isAbort ? '请求已取消' : '网络异常，请检查网络后重试',
      httpStatus: 0,
      retriable: !isAbort,
    })
  }

  const body = response.data
  const businessCode =
    body && typeof body === 'object' && typeof body.code === 'number' ? body.code : status
  const message = errorMessageOf(axiosError)

  if (status === 401) {
    clearFileToken()
    window.dispatchEvent(new CustomEvent(FILE_UNAUTHORIZED_EVENT))
    return new FileApiError({ message: message || '登录已失效，请重新登录', code: businessCode, httpStatus: 401 })
  }
  if (status === 403) {
    // 403 保留 Token，只提示无权限
    window.dispatchEvent(new CustomEvent(FILE_FORBIDDEN_EVENT))
    return new FileApiError({ message: message || '无权限访问该文件', code: businessCode, httpStatus: 403 })
  }

  return new FileApiError({
    message,
    code: businessCode,
    httpStatus: status,
    retriable: RETRIABLE_HTTP_STATUS.has(status),
  })
}

/** 文件请求专用 axios 实例：独立实例，避免与 C 的共享请求层耦合 */
export const fileClient: AxiosInstance = axios.create({
  baseURL: FILE_API_BASE_URL,
  timeout: FILE_REQUEST_TIMEOUT,
})

fileClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readFileToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  if (!config.headers.get('X-Request-Id')) {
    config.headers.set('X-Request-Id', createRequestId())
  }
  return config
})

fileClient.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const body = response.data
    // 业务失败（HTTP 200 但 code != 0）也要当错误抛出
    if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(
        new FileApiError({
          message: body.message || '请求失败',
          code: body.code,
          httpStatus: response.status,
        }),
      )
    }
    return response
  },
  (error: unknown) => Promise.reject(toFileApiError(error)),
)

/** 取出统一响应体里的 data */
function readData<T>(response: AxiosResponse<ApiResult<T>>): T {
  const body = response.data
  return body && typeof body === 'object' && 'data' in body ? body.data : (undefined as T)
}

/** GET /files 等，返回业务 data */
export async function fileGet<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await fileClient.get<ApiResult<T>>(url, config)
  return readData<T>(response)
}

/** POST，返回业务 data */
export async function filePost<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await fileClient.post<ApiResult<T>>(url, data, config)
  return readData<T>(response)
}

/** PUT（分片上传走这里），返回业务 data */
export async function filePut<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await fileClient.put<ApiResult<T>>(url, data, config)
  return readData<T>(response)
}

/** DELETE，返回业务 data */
export async function fileDelete<T = unknown>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await fileClient.delete<ApiResult<T>>(url, config)
  return readData<T>(response)
}
