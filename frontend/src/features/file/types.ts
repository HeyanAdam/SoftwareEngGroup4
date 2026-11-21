/**
 * 文件模块类型定义（B：前端上传引擎）
 *
 * 字段名对齐后端：
 * - `com.group4.kaoyan.modules.file.entity.FileObject`
 * - `com.group4.kaoyan.modules.file.entity.FileUploadSession`
 * - 统一响应 `com.group4.kaoyan.common.R` / `R.PageData`
 *
 * 维护人：B（前端上传引擎）；D 的页面直接复用这里的类型，不再重复定义。
 */

/** 文件场景，与后端 FileService.SCENES 一致，非法值后端返回 400 */
export type FileScene =
  | 'AVATAR'
  | 'QUESTION_IMAGE'
  | 'STUDY_MATERIAL'
  | 'KNOWLEDGE_DOCUMENT'
  | 'CHAT_ATTACHMENT'

/** 场景枚举（下拉框 / 校验用） */
export const FILE_SCENES: readonly FileScene[] = [
  'AVATAR',
  'QUESTION_IMAGE',
  'STUDY_MATERIAL',
  'KNOWLEDGE_DOCUMENT',
  'CHAT_ATTACHMENT',
]

/** 后端 @RequestParam 的默认场景 */
export const DEFAULT_FILE_SCENE: FileScene = 'STUDY_MATERIAL'

/** 上传任务状态（前端状态机，与后端会话状态分开看） */
export type UploadStatus =
  | 'WAITING'
  | 'HASHING'
  | 'UPLOADING'
  | 'PAUSED'
  | 'MERGING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

/** 状态 -> 中文文案 */
export const UPLOAD_STATUS_TEXT: Record<UploadStatus, string> = {
  WAITING: '等待中',
  HASHING: '计算校验值',
  UPLOADING: '上传中',
  PAUSED: '已暂停',
  MERGING: '合并中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

/** 文件对象（后端 FileObject，只读元数据，不含 MinIO 内部地址） */
export interface FileObject {
  id: number
  scene: FileScene
  originalFilename: string
  fileExt?: string | null
  contentType: string
  fileSize: number
  sha256?: string | null
  /** READY / FAILED */
  status: string
  createTime: string
}

/** 分页结构，对齐 R.PageData（snake_case） */
export interface FilePage {
  list: FileObject[]
  total: number
  page_num: number
  page_size: number
}

/** 文件列表查询参数 */
export interface FileListQuery {
  scene?: FileScene
  page?: number
  size?: number
}

/** 上传会话状态，对齐 FileUploadSession 常量 */
export type UploadSessionStatus =
  | 'INIT'
  | 'UPLOADING'
  | 'MERGING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

/**
 * 上传会话（后端 FileUploadSession）。
 * `uploadedParts` / `missingParts` 是《B 指南》约定的进度字段；
 * 当前后端只返回 `uploadedCount`，缺失时前端按 null 处理（见 uploader.ts 注释）。
 */
export interface UploadSession {
  id: number
  originalFilename: string
  fileSize: number
  contentType: string
  fileHash?: string | null
  partSize: number
  partCount: number
  uploadedCount: number
  status: UploadSessionStatus
  expiresAt?: string | null
  uploadedParts?: number[] | null
  missingParts?: number[] | null
}

/** POST /files/upload-sessions 请求体，字段与 InitSessionReq 一致 */
export interface InitUploadSessionRequest {
  filename: string
  fileSize: number
  contentType: string
  partSize: number
  /** SHA-256 整文件哈希，写入会话 fileHash */
  fileHash: string
  scene: FileScene
}

/** 上传任务（前端状态机对象，D 的页面用它渲染） */
export interface UploadTask {
  /** 前端任务 id，与后端 sessionId 无关 */
  id: string
  fileName: string
  fileSize: number
  contentType: string
  scene: FileScene
  status: UploadStatus
  /** 当前阶段百分比 0-100（HASHING 阶段为哈希进度，UPLOADING 阶段为分片进度） */
  progress: number
  /** 分片总数，小文件为 1 */
  partCount: number
  /** 实际分片大小，小文件为整个文件大小 */
  partSize: number
  /** 已完成分片序号（用于展示「已传 3/60」） */
  uploadedParts: number[]
  /** 单任务并发上限 */
  concurrency: number
  /** 后端会话 id（大文件才有） */
  sessionId?: number
  /** 合并成功后的文件 id */
  fileId?: number
  /** 文件指纹：文件名:大小:最后修改时间[:哈希] */
  fingerprint: string
  /** 失败 / 暂停原因 */
  error?: string
  createdAt: number
  updatedAt: number
}

/** 刷新后待续传会话的本地记录 */
export interface ResumeRecord {
  fingerprint: string
  sessionId: number
  scene: FileScene
  fileName: string
  fileSize: number
  lastModified: number
  status: UploadSessionStatus | UploadStatus
  partSize: number
  partCount: number
  fileHash?: string
  uploadedParts: number[]
  updatedAt: number
}

/** UploadTaskCenter 组件对外暴露的完整任务快照（只读用途） */
export interface UploadTaskSnapshot {
  fileName: string
  fileSize: number
  status: UploadStatus
  progress: number
  partCount: number
  uploadedCount: number
  error?: string
}

/**
 * 文件接口端口：上传引擎依赖的最小接口集合。
 * 默认实现是 file.api.ts；单测里注入假实现，避免打网络。
 */
export interface FileApiPort {
  uploadSmall(
    file: File,
    scene: FileScene,
    onProgress?: (percent: number) => void,
    signal?: AbortSignal,
  ): Promise<FileObject>
  uploadBatch(
    files: File[],
    scene: FileScene,
    onProgress?: (percent: number) => void,
  ): Promise<FileObject[]>
  initUploadSession(
    payload: InitUploadSessionRequest,
    idempotencyKey: string,
  ): Promise<UploadSession>
  getUploadSession(sessionId: number): Promise<UploadSession>
  uploadPart(
    sessionId: number,
    partNumber: number,
    blob: Blob,
    hash: string,
  ): Promise<UploadSession>
  completeUploadSession(sessionId: number, scene: FileScene): Promise<FileObject>
  cancelUploadSession(sessionId: number): Promise<void>
}
