/**
 * B：文件接口封装
 *
 * 后端实现：`modules/file/controller/FileController.java`（前缀 /api/v1/files）
 * 所有请求走 `file.http.ts` 的临时客户端；C 完成后由 D 换成 `shared/api/request.ts`。
 *
 * Mock：`isFileMockEnabled()` 为真时整层切到 `file.mock.ts`，联调开关集中在 mock 模块。
 */

import { fileDelete, fileGet, filePost, filePut } from './file.http'
import { isFileMockEnabled, mockFileApi } from './file.mock'
import type {
  FileListQuery,
  FileObject,
  FilePage,
  FileScene,
  InitUploadSessionRequest,
  UploadSession,
} from './types'

const FILES = '/files'

/** 小文件直传 POST /files（`file` + `scene`），同用户同 SHA-256 后端秒传 */
export function uploadSmall(
  file: File,
  scene: FileScene,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<FileObject> {
  if (isFileMockEnabled()) return mockFileApi.uploadSmall(file, scene, onProgress)

  const form = new FormData()
  form.append('file', file, file.name)
  return filePost<FileObject>(FILES, form, {
    params: { scene },
    timeout: 120000,
    signal,
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    },
  })
}

/** 批量直传 POST /files/upload（`files[]` + `scene`） */
export function uploadBatch(
  files: File[],
  scene: FileScene,
  onProgress?: (percent: number) => void,
): Promise<FileObject[]> {
  if (isFileMockEnabled()) return mockFileApi.uploadBatch(files, scene, onProgress)

  const form = new FormData()
  for (const file of files) {
    form.append('files', file, file.name)
  }
  return filePost<FileObject[]>(`${FILES}/upload`, form, {
    params: { scene },
    timeout: 120000,
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    },
  })
}

/** 初始化分片会话 POST /files/upload-sessions（Idempotency-Key 必填） */
export function initUploadSession(
  payload: InitUploadSessionRequest,
  idempotencyKey: string,
): Promise<UploadSession> {
  if (isFileMockEnabled()) return mockFileApi.initUploadSession(payload)

  return filePost<UploadSession>(`${FILES}/upload-sessions`, payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

/** 查询会话进度 GET /files/upload-sessions/{sessionId} */
export function getUploadSession(sessionId: number): Promise<UploadSession> {
  if (isFileMockEnabled()) return mockFileApi.getUploadSession(sessionId)
  return fileGet<UploadSession>(`${FILES}/upload-sessions/${sessionId}`)
}

/** 上传单个分片 PUT /files/upload-sessions/{sessionId}/parts/{partNumber} */
export function uploadPart(
  sessionId: number,
  partNumber: number,
  blob: Blob,
  hash: string,
): Promise<UploadSession> {
  if (isFileMockEnabled()) return mockFileApi.uploadPart(sessionId, partNumber, blob, hash)

  const form = new FormData()
  form.append('part', blob, `${partNumber}.part`)
  return filePut<UploadSession>(
    `${FILES}/upload-sessions/${sessionId}/parts/${partNumber}`,
    form,
    { headers: { 'X-Part-Hash': hash }, timeout: 120000 },
  )
}

/** 合并分片 POST /files/upload-sessions/{sessionId}/complete?scene=... */
export function completeUploadSession(sessionId: number, scene: FileScene): Promise<FileObject> {
  if (isFileMockEnabled()) return mockFileApi.completeUploadSession(sessionId, scene)

  return filePost<FileObject>(
    `${FILES}/upload-sessions/${sessionId}/complete`,
    undefined,
    { params: { scene } },
  )
}

/** 取消会话 DELETE /files/upload-sessions/{sessionId} */
export function cancelUploadSession(sessionId: number): Promise<void> {
  if (isFileMockEnabled()) return mockFileApi.cancelUploadSession(sessionId)
  return fileDelete<void>(`${FILES}/upload-sessions/${sessionId}`)
}

/** 我的文件分页 GET /files?scene=&page=&size= */
export function listFiles(query: FileListQuery = {}): Promise<FilePage> {
  if (isFileMockEnabled()) return mockFileApi.listFiles(query)

  return fileGet<FilePage>(FILES, {
    params: {
      scene: query.scene,
      page: query.page ?? 1,
      size: query.size ?? 10,
    },
  })
}

/** 文件详情 GET /files/{fileId} */
export function getFile(fileId: number): Promise<FileObject> {
  if (isFileMockEnabled()) return mockFileApi.getFile(fileId)
  return fileGet<FileObject>(`${FILES}/${fileId}`)
}

/** 删除文件 DELETE /files/{fileId}（后端逻辑删除 + 删 MinIO 对象） */
export function deleteFile(fileId: number): Promise<void> {
  if (isFileMockEnabled()) return mockFileApi.deleteFile(fileId)
  return fileDelete<void>(`${FILES}/${fileId}`)
}

/** 60 分钟有效的预签名下载地址 GET /files/{fileId}/content */
export async function getDownloadUrl(fileId: number): Promise<string> {
  if (isFileMockEnabled()) return mockFileApi.getDownloadUrl(fileId)

  const data = await fileGet<{ url: string; expiresInMinutes: number }>(
    `${FILES}/${fileId}/content`,
  )
  return data?.url ?? ''
}
