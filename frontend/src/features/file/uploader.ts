/**
 * B：上传引擎（上传状态机）
 *
 * 覆盖《B 指南》第 7–13 章：
 * - 小文件直传 + 批量直传（≤20 个 / 批，≤50MB / 批）
 * - 50MB 阈值判定，超过走分片
 * - SHA-256：整文件在 Worker 增量计算，分片单独计算后放 `X-Part-Hash`
 * - 分片并发（默认 3）、失败重试（1s / 2s / 4s，最多 3 次）
 * - 暂停 / 继续 / 取消
 * - 刷新后从本地记录恢复，仅补缺失分片
 * - 合并完成后发 `completed(file)`
 *
 * 只做 B 的职责：不修改 A 的后端、C 的登录态、D 的页面。
 */

import { reactive, ref } from 'vue'
import * as fileApi from './file.api'
import { FileApiError } from './file.http'
import type {
  FileApiPort,
  FileObject,
  FileScene,
  ResumeRecord,
  UploadSession,
  UploadTask,
} from './types'
// 仅用于类型：worker 消息协议（不引入运行代码）
import type { HashWorkerRequest, HashWorkerResponse } from './hash.worker'

/** 小文件阈值：50MB（与后端 FileService.MAX_DIRECT_SIZE 一致） */
export const SMALL_FILE_MAX_SIZE = 50 * 1024 * 1024

/** 默认分片大小：5MB（与后端 DEFAULT_PART_SIZE 一致） */
export const DEFAULT_PART_SIZE = 5 * 1024 * 1024

/** 默认分片并发数 */
export const DEFAULT_CONCURRENCY = 3

/** 批量直传单批最多文件数 */
export const MAX_BATCH_FILES = 20

/** 批量直传单批最大总字节 */
export const MAX_BATCH_BYTES = 50 * 1024 * 1024

/** 单分片最大自动重试次数 */
export const MAX_PART_RETRIES = 3

/** 全部分片上传后，再向服务端查漏并补传的轮数 */
export const MAX_PART_RECONCILE_ROUNDS = 1

/** 重试等待：1s / 2s / 4s */
export const RETRY_DELAYS_MS = [1000, 2000, 4000]

/** 本地未完成会话存储键 */
export const RESUME_STORAGE_KEY = 'kaoyan.file.upload-sessions.v1'

/** 任务回调 */
export interface UploadEngineOptions {
  concurrency?: number
  /** 文件接口实现，默认 file.api.ts；单测注入假实现 */
  api?: FileApiPort
  /** 哈希实现，默认 Worker；单测注入假实现 */
  hasher?: HashProvider
  /** 分片重试等待序列，默认 1s/2s/4s；单测传短间隔 */
  retryDelays?: number[]
  onCompleted?: (task: UploadTask, file: FileObject) => void
  onFailed?: (task: UploadTask, error: Error) => void
  onProgress?: (task: UploadTask) => void
}

interface TaskContext {
  file: File
  /** Vue 响应式任务对象 */
  task: UploadTask
  paused: boolean
  cancelled: boolean
  running: boolean
  /** 已确认成功的分片序号 */
  doneParts: Set<number>
  /** 初始化会话的幂等键，重试后复用同一个键 */
  idempotencyKey: string
  /** 会话返回的分片大小，用于切片 */
  partSize: number
  /** 整文件 SHA-256，落本地续传记录用 */
  fileHash?: string
  /** 小文件直传请求的取消句柄 */
  abortController?: AbortController
}

// ============ 纯函数工具 ============

/** 是否走小文件直传 */
export function isSmallFile(file: File): boolean {
  return file.size <= SMALL_FILE_MAX_SIZE
}

/** 文件指纹：文件名:大小:最后修改时间[:SHA-256] */
export function createFingerprint(file: File, hash?: string): string {
  const base = `${file.name}:${file.size}:${file.lastModified}`
  return hash ? `${base}:${hash}` : base
}

/** 批量直传拆批：单批 ≤20 个且 ≤50MB，两个条件同时满足 */
export function splitIntoBatches(files: File[]): File[][] {
  const batches: File[][] = []
  let current: File[] = []
  let currentBytes = 0

  for (const file of files) {
    if (!isSmallFile(file)) continue
    const wouldExceedCount = current.length >= MAX_BATCH_FILES
    const wouldExceedBytes = currentBytes + file.size > MAX_BATCH_BYTES
    if (current.length > 0 && (wouldExceedCount || wouldExceedBytes)) {
      batches.push(current)
      current = []
      currentBytes = 0
    }
    current.push(file)
    currentBytes += file.size
  }

  if (current.length > 0) batches.push(current)
  return batches
}

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `id-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

function formatPartNumbers(parts: number[]): string {
  const shown = parts.slice(0, 12).join(',')
  return parts.length > 12 ? `${shown} 等 ${parts.length} 片` : shown
}

// ============ 本地续传记录 ============

function isResumeRecord(value: unknown): value is ResumeRecord {
  if (!value || typeof value !== 'object') return false
  const record = value as Partial<ResumeRecord>
  return (
    typeof record.sessionId === 'number' &&
    typeof record.fileName === 'string' &&
    typeof record.fileSize === 'number' &&
    typeof record.fingerprint === 'string'
  )
}

/** 读取本地未完成会话（刷新后使用） */
export function readResumeRecords(): ResumeRecord[] {
  try {
    const raw = localStorage.getItem(RESUME_STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(isResumeRecord) : []
  } catch {
    return []
  }
}

function writeResumeRecords(records: ResumeRecord[]): void {
  try {
    if (records.length === 0) {
      localStorage.removeItem(RESUME_STORAGE_KEY)
      return
    }
    localStorage.setItem(RESUME_STORAGE_KEY, JSON.stringify(records))
  } catch {
    // 本地存储不可用（隐私模式 / 超配额）时静默降级，续传能力失效但不影响上传
  }
}

function upsertResumeRecords(record: ResumeRecord): void {
  const records = readResumeRecords().filter((item) => item.sessionId !== record.sessionId)
  records.push(record)
  writeResumeRecords(records)
}

/** 移除某条会话记录（按 sessionId） */
export function removeResumeRecord(sessionId: number): void {
  writeResumeRecords(readResumeRecords().filter((item) => item.sessionId !== sessionId))
}

// ============ 哈希 Worker 调度 ============

/** 哈希能力端口：默认走 Worker，单测可注入同步假实现 */
export interface HashProvider {
  hashFile(file: File, onProgress?: (loaded: number, total: number) => void): Promise<string>
  hashBlob(blob: Blob): Promise<string>
}

interface HashJob {
  resolve: (hash: string) => void
  reject: (error: Error) => void
  onProgress?: (loaded: number, total: number) => void
}

class HashService {
  private worker: Worker | null = null

  private readonly jobs = new Map<string, HashJob>()

  private ensureWorker(): Worker {
    if (this.worker) return this.worker
    const worker = new Worker(new URL('./hash.worker.ts', import.meta.url), { type: 'module' })
    worker.onmessage = (event: MessageEvent<HashWorkerResponse>) => {
      this.handleMessage(event.data)
    }
    worker.onerror = () => {
      this.rejectAll(new Error('哈希线程异常，请刷新页面后重试'))
    }
    this.worker = worker
    return worker
  }

  private handleMessage(message: HashWorkerResponse): void {
    const job = this.jobs.get(message.id)
    if (!job) return
    if (message.type === 'progress') {
      job.onProgress?.(message.loaded, message.total)
      return
    }
    if (message.type === 'error') {
      this.jobs.delete(message.id)
      job.reject(new Error(message.message))
      return
    }
    this.jobs.delete(message.id)
    job.resolve(message.hash)
  }

  private rejectAll(error: Error): void {
    for (const job of this.jobs.values()) job.reject(error)
    this.jobs.clear()
    this.worker?.terminate()
    this.worker = null
  }

  private send(request: HashWorkerRequest, onProgress?: HashJob['onProgress']): Promise<string> {
    return new Promise<string>((resolve, reject) => {
      try {
        const worker = this.ensureWorker()
        this.jobs.set(request.id, { resolve, reject, onProgress })
        worker.postMessage(request)
      } catch (error) {
        this.jobs.delete(request.id)
        reject(error instanceof Error ? error : new Error('哈希线程启动失败'))
      }
    })
  }

  /** 整文件哈希（增量，带进度） */
  hashFile(file: File, onProgress?: (loaded: number, total: number) => void): Promise<string> {
    return this.send({ type: 'file', id: newId(), file }, onProgress)
  }

  /** 分片哈希 */
  hashBlob(blob: Blob): Promise<string> {
    return this.send({ type: 'blob', id: newId(), blob })
  }

  dispose(): void {
    this.rejectAll(new Error('上传引擎已销毁'))
  }
}

// ============ 上传引擎 ============

/**
 * 上传引擎：一个实例管理一组上传任务，页面跨路由可保持同一实例。
 * 任务列表是响应式的，D 可直接把 `engine.tasks` 交给 UploadTaskCenter 渲染。
 */
export class UploadEngine {
  private readonly taskRef = ref<UploadTask[]>([])

  private readonly contexts = new Map<string, TaskContext>()

  private readonly options: UploadEngineOptions

  private readonly maxConcurrency: number

  /** 全局分片槽位（所有任务共用，保证同时上传的分片数 ≤ concurrency） */
  private activeSlots = 0

  private readonly slotWaiters: Array<() => void> = []

  /** Token 失效后暂停所有任务 */
  private halted = false

  private hashService: HashService | null = null

  private readonly api: FileApiPort

  private readonly injectedHasher: HashProvider | null

  /** 重试等待序列 */
  private readonly retryDelays: number[]

  constructor(options: UploadEngineOptions = {}) {
    this.options = options
    this.maxConcurrency = Math.max(1, options.concurrency ?? DEFAULT_CONCURRENCY)
    this.api = options.api ?? fileApi
    this.injectedHasher = options.hasher ?? null
    this.retryDelays = options.retryDelays ?? RETRY_DELAYS_MS
  }

  /** 任务列表（响应式，直接用于渲染） */
  get tasks(): UploadTask[] {
    return this.taskRef.value
  }

  private get hasher(): HashProvider {
    if (this.injectedHasher) return this.injectedHasher
    if (!this.hashService) this.hashService = new HashService()
    return this.hashService
  }

  /** 选择文件后加入任务队列（需调用 start 才开始，autoStart 由组件控制） */
  addFiles(files: File[], scene: FileScene): UploadTask[] {
    const created: UploadTask[] = []
    for (const file of files) {
      if (!file || file.size <= 0) continue
      created.push(this.createTask(file, scene).task)
    }
    return created
  }

  /** 启动所有等待中的任务 */
  start(): void {
    const smallGroups = new Map<FileScene, TaskContext[]>()

    for (const ctx of this.contexts.values()) {
      if (ctx.running) continue
      if (ctx.task.status !== 'WAITING') continue
      if (!isSmallFile(ctx.file)) {
        void this.launch(ctx)
        continue
      }
      // 场景分开成批：同一次请求的 scene 一致
      const group = smallGroups.get(ctx.task.scene) ?? []
      group.push(ctx)
      smallGroups.set(ctx.task.scene, group)
    }

    for (const [scene, group] of smallGroups) {
      if (group.length === 1) {
        const only = group[0]
        if (only) void this.launch(only)
        continue
      }
      void this.launchSmallBatch(scene, group)
    }
  }

  /** 暂停：停止启动新分片，等在途分片结束，保留服务端会话 */
  pauseTask(taskId: string): void {
    const ctx = this.contexts.get(taskId)
    if (!ctx) return
    ctx.paused = true
    if (!ctx.running && ctx.task.status === 'WAITING') {
      ctx.task.status = 'PAUSED'
      ctx.task.updatedAt = Date.now()
      this.notify(ctx.task)
    }
  }

  /** 继续：先查会话进度，再只补缺失分片 */
  resumeTask(taskId: string): void {
    const ctx = this.contexts.get(taskId)
    if (!ctx || ctx.running) return
    ctx.paused = false
    ctx.cancelled = false
    this.halted = false
    const t = ctx.task

    if (t.status === 'PAUSED' && t.sessionId) {
      void this.launch(ctx, t.sessionId)
      return
    }
    // 没有会话（小文件）或从未开始：从头重跑
    t.status = 'WAITING'
    t.uploadedParts = []
    ctx.doneParts.clear()
    void this.launch(ctx)
  }

  /** 取消：停分片、取消服务端会话、删本地记录 */
  async cancelTask(taskId: string): Promise<void> {
    const ctx = this.contexts.get(taskId)
    if (!ctx) return
    ctx.cancelled = true
    ctx.paused = false
    const t = ctx.task
    const sessionId = t.sessionId
    ctx.abortController?.abort()
    ctx.abortController = undefined

    // 先停止界面进度，再等待服务端清理，避免取消请求期间进度继续更新。
    t.status = 'CANCELLED'
    t.error = undefined
    t.updatedAt = Date.now()
    this.notify(t)

    if (sessionId) {
      await this.cancelServerSession(sessionId)
    }
  }

  /** 失败任务重试：有会话则续传，无会话则重跑 */
  retryTask(taskId: string): void {
    const ctx = this.contexts.get(taskId)
    if (!ctx || ctx.running) return
    ctx.paused = false
    ctx.cancelled = false
    this.halted = false
    const t = ctx.task
    t.error = undefined

    if (t.sessionId) {
      void this.launch(ctx, t.sessionId)
      return
    }
    t.status = 'WAITING'
    t.progress = 0
    t.uploadedParts = []
    ctx.doneParts.clear()
    ctx.idempotencyKey = newId()
    void this.launch(ctx)
  }

  /** 移除已完成/已取消任务（页面清理用） */
  removeTask(taskId: string): void {
    const ctx = this.contexts.get(taskId)
    if (!ctx) return
    if (ctx.running && ctx.task.status !== 'CANCELLED') return
    this.contexts.delete(taskId)
    this.taskRef.value = this.taskRef.value.filter((task) => task.id !== taskId)
  }

  /**
   * 刷新后恢复：把本地未完成会话与用户重新选择的文件对齐。
   * 文件按「名称 + 大小 + 最后修改时间」匹配，哈希在任务内重新计算后再核对。
   */
  resumeWithFiles(
    records: ResumeRecord[],
    files: File[],
  ): { started: UploadTask[]; unmatched: string[] } {
    const started: UploadTask[] = []
    const unmatched: string[] = []

    for (const record of records) {
      const file = files.find(
        (candidate) =>
          candidate.name === record.fileName &&
          candidate.size === record.fileSize &&
          candidate.lastModified === record.lastModified,
      )
      if (!file) {
        unmatched.push(record.fileName)
        continue
      }
      const ctx = this.createTask(file, record.scene, record)
      started.push(ctx.task)
      void this.launch(ctx, record.sessionId)
    }

    return { started, unmatched }
  }

  dispose(): void {
    this.hashService?.dispose()
    this.hashService = null
  }

  // ============ 内部实现 ============

  private createTask(file: File, scene: FileScene, record?: ResumeRecord): TaskContext {
    const partCount = record?.partCount ?? (isSmallFile(file) ? 1 : Math.ceil(file.size / DEFAULT_PART_SIZE))
    const task = reactive<UploadTask>({
      id: newId(),
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type || 'application/octet-stream',
      scene,
      status: record ? 'PAUSED' : 'WAITING',
      progress: 0,
      partCount,
      partSize: record?.partSize ?? (isSmallFile(file) ? file.size : DEFAULT_PART_SIZE),
      uploadedParts: record ? [...record.uploadedParts] : [],
      concurrency: this.maxConcurrency,
      sessionId: record?.sessionId,
      fingerprint: record?.fingerprint ?? createFingerprint(file),
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })

    const ctx: TaskContext = {
      file,
      task,
      paused: false,
      cancelled: false,
      running: false,
      doneParts: new Set(record?.uploadedParts ?? []),
      idempotencyKey: newId(),
      partSize: record?.partSize ?? DEFAULT_PART_SIZE,
      fileHash: record?.fileHash,
    }

    this.contexts.set(task.id, ctx)
    this.taskRef.value.push(task)
    return ctx
  }

  private async launch(ctx: TaskContext, resumeSessionId?: number): Promise<void> {
    if (ctx.running) return
    ctx.running = true
    try {
      if (resumeSessionId) {
        await this.runChunkedTask(ctx, resumeSessionId)
      } else if (isSmallFile(ctx.file)) {
        await this.runSmallTask(ctx)
      } else {
        await this.runChunkedTask(ctx)
      }
    } catch (error) {
      this.failTask(ctx, error)
    } finally {
      ctx.running = false
      ctx.task.updatedAt = Date.now()
      this.notify(ctx.task)
    }
  }

  /** 小文件单传：先算 SHA-256，再 POST /files */
  private async runSmallTask(ctx: TaskContext): Promise<void> {
    const t = ctx.task
    const hash = await this.hashTaskFile(ctx)
    ctx.fileHash = hash
    t.fingerprint = createFingerprint(ctx.file, hash)
    if (this.stopBeforeUpload(ctx)) return

    t.status = 'UPLOADING'
    t.progress = 0
    this.notify(t)

    const controller = new AbortController()
    ctx.abortController = controller
    let file: FileObject
    try {
      file = await this.api.uploadSmall(
        ctx.file,
        t.scene,
        (percent) => {
          if (ctx.task.status !== 'UPLOADING') return
          t.progress = percent
          this.notify(t)
        },
        controller.signal,
      )
    } finally {
      if (ctx.abortController === controller) ctx.abortController = undefined
    }
    if (ctx.cancelled) return

    t.status = 'COMPLETED'
    t.progress = 100
    t.fileId = file?.id
    t.error = undefined
    this.notify(t)
    if (file) this.options.onCompleted?.(t, file)
  }

  /** 批量直传：同场景小文件按 ≤20 个 / ≤50MB 拆批，逐批 POST /files/upload */
  private async launchSmallBatch(scene: FileScene, group: TaskContext[]): Promise<void> {
    const files = group.map((ctx) => ctx.file)
    const batches = splitIntoBatches(files)
    let cursor = 0
    const runs = batches.map(async (batchFiles) => {
      const batchContexts = group.slice(cursor, cursor + batchFiles.length)
      cursor += batchFiles.length
      await this.runSmallBatch(scene, batchContexts, batchFiles)
    })
    await Promise.all(runs)
  }

  private async runSmallBatch(
    scene: FileScene,
    batchContexts: TaskContext[],
    files: File[],
  ): Promise<void> {
    for (const ctx of batchContexts) {
      ctx.running = true
      ctx.task.status = 'UPLOADING'
      ctx.task.progress = 0
      this.notify(ctx.task)
    }

    try {
      const results = await this.api.uploadBatch(files, scene, (percent) => {
        for (const ctx of batchContexts) {
          if (ctx.task.status === 'UPLOADING') {
            ctx.task.progress = percent
            this.notify(ctx.task)
          }
        }
      })

      batchContexts.forEach((ctx, index) => {
        const file = results[index]
        if (!file) {
          this.failTask(ctx, new Error('批量上传返回结果缺失'))
          return
        }
        ctx.task.status = 'COMPLETED'
        ctx.task.progress = 100
        ctx.task.fileId = file.id
        ctx.task.error = undefined
        this.notify(ctx.task)
        this.options.onCompleted?.(ctx.task, file)
      })
    } catch (error) {
      for (const ctx of batchContexts) this.failTask(ctx, error)
    } finally {
      for (const ctx of batchContexts) {
        ctx.running = false
        ctx.task.updatedAt = Date.now()
      }
    }
  }

  /**
   * 大文件分片：哈希 → 初始化/查询会话 → 并发补片 → 合并
   * @param resumeSessionId 有值时走「刷新后续传」，只补缺失分片
   */
  private async runChunkedTask(ctx: TaskContext, resumeSessionId?: number): Promise<void> {
    const t = ctx.task
    t.error = undefined
    const hash = await this.hashTaskFile(ctx)
    ctx.fileHash = hash
    t.fingerprint = createFingerprint(ctx.file, hash)
    if (this.stopBeforeUpload(ctx)) return

    let session: UploadSession | null = null

    if (resumeSessionId) {
      const record = readResumeRecords().find((item) => item.sessionId === resumeSessionId)
      // 内容变了（哈希不一致）就丢弃旧会话，重建
      const hashMismatch = Boolean(record?.fileHash && record.fileHash !== hash)
      if (hashMismatch) {
        removeResumeRecord(resumeSessionId)
      } else {
        try {
          session = await this.api.getUploadSession(resumeSessionId)
        } catch {
          // 会话已过期 / 被清理，退化成全新上传
          removeResumeRecord(resumeSessionId)
        }
      }
      if (!session) {
        ctx.doneParts.clear()
        t.uploadedParts = []
        t.sessionId = undefined
      }
    }

    if (!session) {
      session = await this.api.initUploadSession(
        {
          filename: ctx.file.name,
          fileSize: ctx.file.size,
          contentType: ctx.file.type || 'application/octet-stream',
          partSize: DEFAULT_PART_SIZE,
          fileHash: hash,
          scene: t.scene,
        },
        ctx.idempotencyKey,
      )
    }

    if (ctx.cancelled) {
      await this.cancelServerSession(session.id)
      t.status = 'CANCELLED'
      this.notify(t)
      return
    }

    t.sessionId = session.id
    ctx.partSize = session.partSize || DEFAULT_PART_SIZE
    t.partSize = ctx.partSize
    t.partCount = session.partCount
    this.applySessionProgress(ctx, session)
    this.persist(ctx)

    if (ctx.paused) {
      t.status = 'PAUSED'
      this.notify(t)
      return
    }

    let missing = this.missingPartsOf(ctx, session)

    // 先补当前已知缺口，再回查服务端确认。一次“请求成功但响应丢失”
    // 或单片最终失败时，不会直接带着 71/72 去调用 merge。
    for (let round = 0; round <= MAX_PART_RECONCILE_ROUNDS && missing.length > 0; round++) {
      if (session.status === 'COMPLETED' || session.status === 'MERGING') break
      t.status = 'UPLOADING'
      t.error =
        round === 0
          ? undefined
          : `服务端仍缺少分片 ${formatPartNumbers(missing)}，正在补传（${round}/${MAX_PART_RECONCILE_ROUNDS}）`
      this.notify(t)
      await this.uploadParts(ctx, missing)

      if (ctx.cancelled || ctx.paused) break

      // 以服务端落库明细为准，不能只信本地“这个请求返回 200”。
      session = await this.api.getUploadSession(session.id)
      this.applySessionProgress(ctx, session)
      missing = this.missingPartsOf(ctx, session)
    }

    if (ctx.cancelled) {
      t.status = 'CANCELLED'
      this.notify(t)
      return
    }
    if (ctx.paused && session.status !== 'COMPLETED') {
      t.status = 'PAUSED'
      this.persist(ctx)
      this.notify(t)
      return
    }
    if (missing.length > 0) {
      throw new FileApiError({
        message: `分片仍未全部确认，缺少第 ${formatPartNumbers(missing)} 片，请重试`,
        code: 409,
        httpStatus: 409,
        retriable: true,
      })
    }

    // 合并（重复 complete 时后端返回同一文件对象）
    t.status = 'MERGING'
    t.error = undefined
    t.progress = 100
    this.notify(t)

    const file = await this.api.completeUploadSession(session.id, t.scene)
    t.status = 'COMPLETED'
    t.fileId = file?.id
    t.error = undefined
    removeResumeRecord(session.id)
    this.notify(t)
    if (file) this.options.onCompleted?.(t, file)
  }

  /** 会话进度写回本地任务；服务端返回明细时以服务端为准。 */
  private applySessionProgress(ctx: TaskContext, session: UploadSession): void {
    if (Array.isArray(session.uploadedParts)) {
      ctx.doneParts = new Set(session.uploadedParts)
    }
    if (Array.isArray(session.missingParts)) {
      for (const part of session.missingParts) ctx.doneParts.delete(part)
    }
    ctx.task.uploadedParts = Array.from(ctx.doneParts).sort((left, right) => left - right)
    this.updateProgress(ctx)
  }

  /**
   * 计算需要补传的分片。
   * 优先用后端返回的 missingParts / uploadedParts（《B 指南》约定字段）；
   * 后端当前只回 uploadedCount 时，用本地已完成集合求差，最坏情况全量重传（后端 upsert 幂等）。
   */
  private missingPartsOf(ctx: TaskContext, session: UploadSession): number[] {
    const all: number[] = []
    for (let part = 1; part <= session.partCount; part++) all.push(part)

    // 空数组也是有效答案，表示服务端确认已经收齐；不能当成“字段缺失”。
    if (Array.isArray(session.missingParts)) {
      return [...new Set(session.missingParts)].sort((left, right) => left - right)
    }
    if (Array.isArray(session.uploadedParts)) {
      const uploaded = new Set(session.uploadedParts)
      return all.filter((part) => !uploaded.has(part))
    }
    return all.filter((part) => !ctx.doneParts.has(part))
  }

  /** 并发上传分片，每个 worker 循环取队列 */
  private async uploadParts(ctx: TaskContext, missing: number[]): Promise<void> {
    const queue = [...missing]
    const failedParts = new Set<number>()
    const workers = Math.min(this.maxConcurrency, Math.max(1, queue.length))
    const tasks: Promise<void>[] = []
    for (let index = 0; index < workers; index++) {
      tasks.push(this.partWorker(ctx, queue, failedParts))
    }
    await Promise.allSettled(tasks)
    if (this.halted) {
      throw new FileApiError({ message: '登录已失效，请重新登录', httpStatus: 401 })
    }
    if (failedParts.size > 0) {
      ctx.task.error = `以下分片重试后仍失败：${formatPartNumbers(
        [...failedParts].sort((left, right) => left - right),
      )}，正在向服务端核对`
      this.notify(ctx.task)
    }
  }

  private async partWorker(
    ctx: TaskContext,
    queue: number[],
    failedParts: Set<number>,
  ): Promise<void> {
    while (queue.length > 0) {
      if (ctx.cancelled || ctx.paused) return
      if (this.halted) throw new FileApiError({ message: '登录已失效，请重新登录', httpStatus: 401 })
      const partNumber = queue.shift()
      if (partNumber === undefined) return
      if (ctx.doneParts.has(partNumber)) continue
      try {
        await this.uploadOnePart(ctx, partNumber)
        ctx.doneParts.add(partNumber)
        ctx.task.uploadedParts = Array.from(ctx.doneParts).sort((left, right) => left - right)
        this.updateProgress(ctx)
        this.persist(ctx)
        this.notify(ctx.task)
      } catch (error) {
        if (ctx.cancelled || ctx.paused) return
        const apiError = error instanceof FileApiError ? error : null
        if (apiError?.httpStatus === 401 || apiError?.httpStatus === 403) {
          this.halted = apiError.httpStatus === 401
          throw error
        }
        // 单片失败不终止其他 worker；等全部结束后回查服务端，只补真正的缺片。
        failedParts.add(partNumber)
        ctx.task.error = `分片 ${partNumber} 上传失败：${
          error instanceof Error ? error.message : '未知错误'
        }`
        this.notify(ctx.task)
      }
    }
  }

  /** 单分片上传 + 自动重试 */
  private async uploadOnePart(ctx: TaskContext, partNumber: number): Promise<void> {
    const sessionId = ctx.task.sessionId
    if (!sessionId) throw new Error('上传会话不存在')

    const start = (partNumber - 1) * ctx.partSize
    const end = Math.min(start + ctx.partSize, ctx.file.size)
    const blob = ctx.file.slice(start, end)
    const partHash = await this.hasher.hashBlob(blob)

    for (let attempt = 0; attempt <= MAX_PART_RETRIES; attempt++) {
      if (ctx.cancelled || ctx.paused) return
      await this.acquireSlot()
      try {
        await this.api.uploadPart(sessionId, partNumber, blob, partHash)
        ctx.task.error = undefined
        return
      } catch (error) {
        if (ctx.cancelled) return
        const apiError = error instanceof FileApiError ? error : null
        const retriable = apiError ? apiError.retriable : true
        if (!retriable || attempt === MAX_PART_RETRIES) throw error
        ctx.task.error = `${apiError?.message ?? '分片上传失败'}，正在重试（${attempt + 1}/${MAX_PART_RETRIES}）`
        this.notify(ctx.task)
      } finally {
        this.releaseSlot()
      }
      await sleep(this.retryDelays[attempt] ?? 4000)
    }
  }

  private async hashTaskFile(ctx: TaskContext): Promise<string> {
    const t = ctx.task
    t.status = 'HASHING'
    t.progress = 0
    this.notify(t)
    return this.hasher.hashFile(ctx.file, (loaded, total) => {
      if (t.status !== 'HASHING') return
      if (total <= 0) return
      t.progress = Math.min(100, Math.round((loaded / total) * 100))
      this.notify(t)
    })
  }

  /** 哈希结束后先检查暂停/取消，避免取消后仍创建会话或发起直传。 */
  private stopBeforeUpload(ctx: TaskContext): boolean {
    const t = ctx.task
    if (ctx.cancelled) {
      t.status = 'CANCELLED'
      t.error = undefined
      this.notify(t)
      return true
    }
    if (ctx.paused) {
      t.status = 'PAUSED'
      this.notify(t)
      return true
    }
    return false
  }

  private async cancelServerSession(sessionId: number): Promise<void> {
    try {
      await this.api.cancelUploadSession(sessionId)
    } catch {
      // 会话可能已合并 / 过期被清理，取消失败不影响本地状态
    }
    removeResumeRecord(sessionId)
  }

  private updateProgress(ctx: TaskContext): void {
    const t = ctx.task
    if (t.status === 'CANCELLED') return
    let doneBytes = 0
    for (const part of ctx.doneParts) {
      const start = (part - 1) * ctx.partSize
      doneBytes += Math.min(ctx.partSize, t.fileSize - start)
    }
    t.progress = t.fileSize > 0 ? Math.min(99, Math.round((doneBytes / t.fileSize) * 100)) : 0
  }

  private persist(ctx: TaskContext): void {
    const t = ctx.task
    if (!t.sessionId) return
    if (t.status === 'COMPLETED' || t.status === 'CANCELLED') return
    upsertResumeRecords({
      fingerprint: t.fingerprint,
      sessionId: t.sessionId,
      scene: t.scene,
      fileName: t.fileName,
      fileSize: t.fileSize,
      lastModified: ctx.file.lastModified,
      status: 'UPLOADING',
      partSize: ctx.partSize,
      partCount: t.partCount,
      fileHash: ctx.fileHash,
      uploadedParts: [...t.uploadedParts],
      updatedAt: Date.now(),
    })
  }

  private failTask(ctx: TaskContext, error: unknown): void {
    const t = ctx.task
    if (ctx.cancelled) {
      t.status = 'CANCELLED'
      t.updatedAt = Date.now()
      this.notify(t)
      return
    }
    const apiError = error instanceof FileApiError ? error : null
    if (apiError?.httpStatus === 401) {
      // Token 失效：停止所有任务，等用户重新登录后再重试
      this.halted = true
    }
    t.status = 'FAILED'
    t.error = error instanceof Error ? error.message : '上传失败'
    t.updatedAt = Date.now()
    this.notify(t)
    this.options.onFailed?.(t, error instanceof Error ? error : new Error(String(error)))
  }

  private notify(task: UploadTask): void {
    task.updatedAt = Date.now()
    this.options.onProgress?.(task)
  }

  private async acquireSlot(): Promise<void> {
    if (this.activeSlots < this.maxConcurrency) {
      this.activeSlots++
      return
    }
    await new Promise<void>((resolve) => {
      this.slotWaiters.push(resolve)
    })
    this.activeSlots++
  }

  private releaseSlot(): void {
    this.activeSlots = Math.max(0, this.activeSlots - 1)
    const next = this.slotWaiters.shift()
    if (next) next()
  }
}
