/**
 * B：Mock 联调模块（《B 指南》§15）
 *
 * 开关与场景集中在这一个模块里，A 的接口可用后切回真实接口不用改别处。
 * - 开启：`localStorage.setItem('kaoyan.file.mock', 'on')`，或调用 `enableFileMock()`
 * - 关闭：`disableFileMock()` / 删掉该键
 * - 场景：`localStorage.setItem('kaoyan.file.mock.scenario', 'unauthorized' | 'hash-error' | 'network')`
 *
 * 覆盖指南要求的模拟点：小文件固定 fileId、大文件固定会话和分片数、部分分片已传、
 * 刷新后缺 3 个分片、401、400 哈希错误、网络中断。
 */

import { FileApiError } from './file.http'
import type {
  FileListQuery,
  FileObject,
  FilePage,
  FileScene,
  InitUploadSessionRequest,
  UploadSession,
} from './types'

export type MockScenario = 'normal' | 'unauthorized' | 'hash-error' | 'network'

const FLAG_KEY = 'kaoyan.file.mock'
const SCENARIO_KEY = 'kaoyan.file.mock.scenario'

/** 与后端 DEFAULT_PART_SIZE 保持一致（此处不引入 uploader，避免循环依赖） */
const MOCK_PART_SIZE = 5 * 1024 * 1024

/** 固定小文件 id / 固定会话 id，便于和 D、A 对日志 */
const MOCK_FILE_ID = 9001
const MOCK_COMPLETED_FILE_ID = 9002
const MOCK_SESSION_ID = 7001

export function isFileMockEnabled(): boolean {
  try {
    return localStorage.getItem(FLAG_KEY) === 'on'
  } catch {
    return false
  }
}

export function getMockScenario(): MockScenario {
  try {
    const value = localStorage.getItem(SCENARIO_KEY)
    if (value === 'unauthorized' || value === 'hash-error' || value === 'network') return value
    return 'normal'
  } catch {
    return 'normal'
  }
}

export function enableFileMock(scenario: MockScenario = 'normal'): void {
  localStorage.setItem(FLAG_KEY, 'on')
  localStorage.setItem(SCENARIO_KEY, scenario)
}

export function disableFileMock(): void {
  localStorage.removeItem(FLAG_KEY)
  localStorage.removeItem(SCENARIO_KEY)
}

function mockFile(scene: FileScene, filename: string, size: number, id = MOCK_FILE_ID): FileObject {
  return {
    id,
    scene,
    originalFilename: filename,
    fileExt: filename.includes('.') ? filename.split('.').pop()?.toLowerCase() : null,
    contentType: 'application/octet-stream',
    fileSize: size,
    sha256: 'mock-sha256',
    status: 'READY',
    createTime: new Date().toISOString(),
  }
}

function scenarioError(): FileApiError | null {
  const scenario = getMockScenario()
  if (scenario === 'unauthorized') {
    return new FileApiError({ message: '未登录或登录已失效（mock）', code: 10001, httpStatus: 401 })
  }
  if (scenario === 'hash-error') {
    return new FileApiError({ message: '分片哈希校验失败（mock）', code: 10000, httpStatus: 400 })
  }
  if (scenario === 'network') {
    return new FileApiError({ message: '网络异常，请检查网络后重试（mock）', httpStatus: 503, retriable: true })
  }
  return null
}

/** 会话进度：前 N-3 片已传，缺最后 3 片（模拟刷新后缺 3 个分片） */
function mockSession(session: UploadSession): UploadSession {
  const partCount = session.partCount
  if (partCount <= 3) {
    return { ...session, uploadedParts: [], missingParts: Array.from({ length: partCount }, (_, i) => i + 1) }
  }
  const uploaded = Array.from({ length: partCount - 3 }, (_, i) => i + 1)
  const missing = [partCount - 2, partCount - 1, partCount]
  return { ...session, uploadedParts: uploaded, missingParts: missing, uploadedCount: uploaded.length }
}

const sessionStore = new Map<number, UploadSession>()

 /** Mock 版文件接口，签名与 file.api.ts 完全一致 */
export const mockFileApi = {
  uploadSmall(
    _file: File,
    scene: FileScene,
    _onProgress?: (percent: number) => void,
  ): Promise<FileObject> {
    const error = scenarioError()
    if (error) return Promise.reject(error)
    return Promise.resolve(mockFile(scene, _file.name, _file.size))
  },

  uploadBatch(
    files: File[],
    scene: FileScene,
    _onProgress?: (percent: number) => void,
  ): Promise<FileObject[]> {
    const error = scenarioError()
    if (error) return Promise.reject(error)
    return Promise.resolve(files.map((file, index) => mockFile(scene, file.name, file.size, MOCK_FILE_ID + index)))
  },

  initUploadSession(payload: InitUploadSessionRequest): Promise<UploadSession> {
    const error = scenarioError()
    if (error) return Promise.reject(error)
    const partSize = payload.partSize || MOCK_PART_SIZE
    const partCount = Math.ceil(payload.fileSize / partSize)
    const session: UploadSession = {
      id: MOCK_SESSION_ID,
      originalFilename: payload.filename,
      fileSize: payload.fileSize,
      contentType: payload.contentType,
      fileHash: payload.fileHash,
      partSize,
      partCount,
      uploadedCount: 0,
      status: 'INIT',
      uploadedParts: [],
      missingParts: Array.from({ length: partCount }, (_, i) => i + 1),
    }
    sessionStore.set(session.id, session)
    return Promise.resolve(session)
  },

  getUploadSession(sessionId: number): Promise<UploadSession> {
    const error = scenarioError()
    if (error) return Promise.reject(error)
    const session = sessionStore.get(sessionId)
    if (!session) {
      return Promise.reject(new FileApiError({ message: '上传会话不存在（mock）', httpStatus: 404 }))
    }
    return Promise.resolve(mockSession(session))
  },

  uploadPart(
    sessionId: number,
    _partNumber: number,
    _blob: Blob,
    _hash: string,
  ): Promise<UploadSession> {
    const error = scenarioError()
    if (error) return Promise.reject(error)
    const session = sessionStore.get(sessionId)
    if (!session) {
      return Promise.reject(new FileApiError({ message: '上传会话不存在（mock）', httpStatus: 404 }))
    }
    session.status = 'UPLOADING'
    session.uploadedCount = Math.min(session.partCount, session.uploadedCount + 1)
    return Promise.resolve(session)
  },

  completeUploadSession(sessionId: number, scene: FileScene): Promise<FileObject> {
    const error = scenarioError()
    if (error) return Promise.reject(error)
    const session = sessionStore.get(sessionId)
    if (!session) {
      return Promise.reject(new FileApiError({ message: '上传会话不存在（mock）', httpStatus: 404 }))
    }
    sessionStore.delete(sessionId)
    return Promise.resolve(
      mockFile(scene, session.originalFilename, session.fileSize, MOCK_COMPLETED_FILE_ID),
    )
  },

  cancelUploadSession(sessionId: number): Promise<void> {
    sessionStore.delete(sessionId)
    return Promise.resolve()
  },

  listFiles(query: FileListQuery = {}): Promise<FilePage> {
    const items = [mockFile(query.scene ?? 'STUDY_MATERIAL', 'mock-report.pdf', 1024)]
    return Promise.resolve({ list: items, total: items.length, page_num: 1, page_size: 10 })
  },

  getFile(fileId: number): Promise<FileObject> {
    return Promise.resolve(mockFile('STUDY_MATERIAL', `mock-${fileId}.pdf`, 1024, fileId))
  },

  deleteFile(_fileId: number): Promise<void> {
    return Promise.resolve()
  },

  getDownloadUrl(fileId: number): Promise<string> {
    return Promise.resolve(`https://mock.local/files/${fileId}?signature=mock`)
  },
}
