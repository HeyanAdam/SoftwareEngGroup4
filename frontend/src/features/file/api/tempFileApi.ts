/**
 * 临时文件接口层（D 自用，B 交付 file.api.ts 后整体删除）
 *
 * 说明：
 * 1. 全部走 C 的共享 request，不新建 axios 实例、不手写 Token。
 * 2. 路径相对 /api/v1，真实接口以 FileController 为准：
 *    GET    /files                       列表（scene/page/size，无 keyword）
 *    GET    /files/{id}                  详情
 *    GET    /files/{id}/content          预签名下载地址（{ url, expiresInMinutes }）
 *    DELETE /files/{id}                  删除
 *    POST   /files?scene=               单文件直传（字段名 file）
 *    POST   /files/upload?scene=        批量直传（字段名 files）
 *    POST   /files/upload-sessions      初始化分片会话
 *    GET    /files/upload-sessions/{id} 查询会话（只有 uploadedCount，没有已传分片清单）
 *    PUT    /files/upload-sessions/{id}/parts/{n}  上传分片（字段名 part）
 *    POST   /files/upload-sessions/{id}/complete?scene=  合并
 *    DELETE /files/upload-sessions/{id} 取消会话（后端不删 MinIO 分片，见 A 待办）
 */
import { del, get, post, put, upload } from '../../../shared/api/request'

/** 直传上限，与后端 FileService.MAX_DIRECT_SIZE 一致（50MB） */
export const DIRECT_MAX_SIZE = 50 * 1024 * 1024
/** 分片大小下限/上限，与后端 MIN_PART_SIZE / MAX_PART_SIZE 一致 */
export const MIN_PART_SIZE = 1024 * 1024
export const MAX_PART_SIZE = 100 * 1024 * 1024
/** 默认分片大小，与后端 DEFAULT_PART_SIZE 一致 */
export const DEFAULT_PART_SIZE = 5 * 1024 * 1024

export type FileScene =
  | 'STUDY_MATERIAL'
  | 'CHAT_ATTACHMENT'
  | 'QUESTION_IMAGE'
  | 'KNOWLEDGE_DOCUMENT'
  | 'AVATAR'

export interface FileObjectVO {
  id: number
  ownerUserId: number
  scene: string
  bucketName: string
  objectKey: string
  originalFilename: string
  fileExt: string
  contentType: string
  fileSize: number
  sha256: string
  status: string
  deleted: number
  createTime: string
  updateTime: string
}

/** 后端 R.PageData 的字段是 list / total / page_num / page_size，不是 records */
export interface FilePageData {
  list: FileObjectVO[]
  total: number
  page_num: number
  page_size: number
}

export interface FileUploadSessionVO {
  id: number
  userId: number
  originalFilename: string
  fileSize: number
  contentType: string
  fileHash: string
  partSize: number
  partCount: number
  uploadedCount: number
  bucketName: string
  objectKey: string
  status: string
  expiresAt: string
  createTime: string
  updateTime: string
}

export interface InitSessionPayload {
  filename: string
  fileSize: number
  contentType: string
  partSize: number
  fileHash: string
  scene: FileScene
}

/** 列表：scene 为空表示全部；后端不支持 keyword，搜索在前端当前页过滤 */
export function listFiles(params: {
  scene?: string
  page: number
  size: number
}): Promise<FilePageData> {
  return get<FilePageData>('/files', { params })
}

export function getFileDetail(fileId: number): Promise<FileObjectVO> {
  return get<FileObjectVO>(`/files/${fileId}`)
}

/** 预签名下载地址，60 分钟有效；不要用 axios 拉二进制 */
export function getDownloadUrl(
  fileId: number,
): Promise<{ url: string; expiresInMinutes: number }> {
  return get<{ url: string; expiresInMinutes: number }>(`/files/${fileId}/content`)
}

export function deleteFile(fileId: number): Promise<void> {
  return del<void>(`/files/${fileId}`)
}

/** 单文件直传：multipart 字段名必须是 file；超过 50MB 后端返回 413 */
export function uploadDirect(file: File, scene: FileScene): Promise<FileObjectVO> {
  const formData = new FormData()
  formData.append('file', file)
  return upload<FileObjectVO>(`/files?scene=${encodeURIComponent(scene)}`, formData)
}

/** 批量直传：multipart 字段名必须是 files；后端实际是循环单传 */
export function uploadBatch(files: File[], scene: FileScene): Promise<FileObjectVO[]> {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return upload<FileObjectVO[]>(`/files/upload?scene=${encodeURIComponent(scene)}`, formData)
}

export function initUploadSession(
  payload: InitSessionPayload,
): Promise<FileUploadSessionVO> {
  return post<FileUploadSessionVO>('/files/upload-sessions', payload)
}

export function getUploadSession(sessionId: number): Promise<FileUploadSessionVO> {
  return get<FileUploadSessionVO>(`/files/upload-sessions/${sessionId}`)
}

/** 上传分片：字段名必须是 part；后端按字节精确比对大小，切多了直接 400 */
export function uploadPart(
  sessionId: number,
  partNumber: number,
  part: Blob,
  partHash?: string,
): Promise<FileUploadSessionVO> {
  const formData = new FormData()
  formData.append('part', part, `part-${partNumber}`)
  return put<FileUploadSessionVO>(
    `/files/upload-sessions/${sessionId}/parts/${partNumber}`,
    formData,
    partHash ? { headers: { 'X-Part-Hash': partHash } } : undefined,
  )
}

/** 合并：必须带合法 scene；合并失败会话会进 FAILED，之后不能重复 complete */
export function completeUploadSession(
  sessionId: number,
  scene: FileScene,
): Promise<FileObjectVO> {
  return post<FileObjectVO>(
    `/files/upload-sessions/${sessionId}/complete?scene=${encodeURIComponent(scene)}`,
  )
}

export function cancelUploadSession(sessionId: number): Promise<void> {
  return del<void>(`/files/upload-sessions/${sessionId}`)
}

/** 计算 SHA-256（Web Crypto），用于秒传与会话 fileHash */
export async function sha256Of(file: Blob): Promise<string> {
  const buffer = await file.arrayBuffer()
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * 按 partSize 精确切片，最后一片是余数。
 * 切片必须严格按字节边界，否则后端 expectedPartSize 校验会 400。
 */
export function splitFile(file: File, partSize = DEFAULT_PART_SIZE): Blob[] {
  const parts: Blob[] = []
  for (let start = 0; start < file.size; start += partSize) {
    parts.push(file.slice(start, Math.min(start + partSize, file.size)))
  }
  return parts
}
