/**
 * B：上传引擎状态机测试（《B 指南》§16 / §18）
 *
 * 用注入的假接口 + 假哈希实现跑，不打网络、不起 Worker。
 * 覆盖：阈值判定、拆批、小文件直传、分片会话、并发上限、重试、暂停/继续、取消、401、续传记录。
 */

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FileApiError } from '../file.http'
import type { FileApiPort, FileObject, FileScene, UploadSession } from '../types'
import {
  DEFAULT_PART_SIZE,
  MAX_BATCH_FILES,
  MAX_PART_RETRIES,
  RESUME_STORAGE_KEY,
  SMALL_FILE_MAX_SIZE,
  UploadEngine,
  createFingerprint,
  isSmallFile,
  readResumeRecords,
  removeResumeRecord,
  splitIntoBatches,
} from '../uploader'

/** 构造只有 size / lastModified 有意义的 File（底层数据只有 1 字节，够切片用） */
function fakeFile(name: string, size: number, lastModified = 1700000000000): File {
  const file = new File([new Uint8Array(1)], name, { type: 'text/plain', lastModified })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function fileObject(scene: FileScene, file: File, id: number): FileObject {
  return {
    id,
    scene,
    originalFilename: file.name,
    contentType: 'application/octet-stream',
    fileSize: file.size,
    status: 'READY',
    createTime: '2026-11-20T10:00:00',
  }
}

interface FakeApiStats {
  uploadSmall: number
  uploadBatch: File[][]
  initSession: number
  uploadParts: number[]
  uploadPartAttempts: number[]
  complete: number
  cancel: number[]
  maxActiveParts: number
  completedFileId?: number
}

interface FakeApiOptions {
  /** 每次 uploadSmall / uploadPart 都抛 401 */
  unauthorized?: boolean
  /** 前 N 次 uploadPart 抛 503（可重试） */
  transientPartFailures?: number
  /** 分片上传耗时，用于观察并发与取消窗口 */
  partDelayMs?: number
  /** 指定分片前 N 次请求失败；用于验证查漏后的补传 */
  partFailuresByNumber?: Record<number, number>
  /** 会话初始化时模拟服务端已经收到的分片 */
  initialUploadedParts?: number[]
  /** 小文件直传耗时，用于观察取消窗口 */
  smallDelayMs?: number
}

function createFakeApi(options: FakeApiOptions = {}): { api: FileApiPort; stats: FakeApiStats } {
  const sessions = new Map<number, UploadSession>()
  let sessionSeq = 100
  let transientLeft = options.transientPartFailures ?? 0
  let activeParts = 0
  const failuresByPart = new Map(
    Object.entries(options.partFailuresByNumber ?? {}).map(([part, count]) => [
      Number(part),
      count,
    ]),
  )

  const stats: FakeApiStats = {
    uploadSmall: 0,
    uploadBatch: [],
    initSession: 0,
    uploadParts: [],
    uploadPartAttempts: [],
    complete: 0,
    cancel: [],
    maxActiveParts: 0,
  }

  async function delay(): Promise<void> {
    if (!options.partDelayMs) return
    await new Promise((resolve) => {
      setTimeout(resolve, options.partDelayMs)
    })
  }

  function refreshParts(session: UploadSession, uploaded: Set<number>): UploadSession {
    session.uploadedParts = [...uploaded].sort((left, right) => left - right)
    session.missingParts = Array.from(
      { length: session.partCount },
      (_, index) => index + 1,
    ).filter((part) => !uploaded.has(part))
    session.uploadedCount = session.uploadedParts.length
    return { ...session }
  }

  const api: FileApiPort = {
    async uploadSmall(file, scene, _onProgress, signal) {
      if (options.unauthorized) {
        throw new FileApiError({ message: '登录已失效，请重新登录', httpStatus: 401 })
      }
      stats.uploadSmall++
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      if (options.smallDelayMs) {
        await new Promise<void>((resolve, reject) => {
          const timer = setTimeout(resolve, options.smallDelayMs)
          signal?.addEventListener(
            'abort',
            () => {
              clearTimeout(timer)
              reject(new DOMException('Aborted', 'AbortError'))
            },
            { once: true },
          )
        })
      }
      return fileObject(scene, file, 1001)
    },
    async uploadBatch(files, scene) {
      stats.uploadBatch.push([...files])
      return files.map((file, index) => fileObject(scene, file, 2000 + index))
    },
    async initUploadSession(payload) {
      stats.initSession++
      const partSize = payload.partSize || DEFAULT_PART_SIZE
      const session: UploadSession = {
        id: sessionSeq++,
        originalFilename: payload.filename,
        fileSize: payload.fileSize,
        contentType: payload.contentType,
        fileHash: payload.fileHash,
        partSize,
        partCount: Math.ceil(payload.fileSize / partSize),
        uploadedCount: 0,
        status: 'INIT',
        uploadedParts: [],
        missingParts: [],
      }
      const uploaded = new Set(
        (options.initialUploadedParts ?? []).filter(
          (part) => part >= 1 && part <= session.partCount,
        ),
      )
      refreshParts(session, uploaded)
      sessions.set(session.id, session)
      return { ...session }
    },
    async getUploadSession(sessionId) {
      const session = sessions.get(sessionId)
      if (!session) throw new FileApiError({ message: '会话不存在', httpStatus: 404 })
      return { ...session }
    },
    async uploadPart(sessionId, partNumber) {
      activeParts++
      stats.maxActiveParts = Math.max(stats.maxActiveParts, activeParts)
      stats.uploadPartAttempts.push(partNumber)
      try {
        if (options.unauthorized) {
          throw new FileApiError({ message: '登录已失效，请重新登录', httpStatus: 401 })
        }
        const remainingPartFailures = failuresByPart.get(partNumber) ?? 0
        if (remainingPartFailures > 0) {
          failuresByPart.set(partNumber, remainingPartFailures - 1)
          throw new FileApiError({ message: `分片 ${partNumber} 暂时失败`, httpStatus: 503, retriable: true })
        }
        if (transientLeft > 0) {
          transientLeft--
          throw new FileApiError({ message: '网络异常，请重试', httpStatus: 503, retriable: true })
        }
        await delay()
        const session = sessions.get(sessionId)
        if (!session) throw new FileApiError({ message: '会话不存在', httpStatus: 404 })
        const uploaded = new Set(session.uploadedParts ?? [])
        uploaded.add(partNumber)
        refreshParts(session, uploaded)
        stats.uploadParts.push(partNumber)
        return { ...session }
      } finally {
        activeParts--
      }
    },
    async completeUploadSession(sessionId, scene) {
      stats.complete++
      const session = sessions.get(sessionId)
      if (!session) throw new FileApiError({ message: '会话不存在', httpStatus: 404 })
      if ((session.missingParts?.length ?? 0) > 0) {
        throw new FileApiError({
          message: `分片未上传完整，缺少：${session.missingParts?.join(',')}`,
          httpStatus: 409,
        })
      }
      stats.completedFileId = stats.completedFileId ?? 3000
      return fileObject(scene, fakeFile(session.originalFilename, session.fileSize), 3000)
    },
    async cancelUploadSession(sessionId) {
      stats.cancel.push(sessionId)
      sessions.delete(sessionId)
    },
  }

  return { api, stats }
}

const fakeHasher = {
  hashFile: async () => 'hash-of-whole-file',
  hashBlob: async () => 'hash-of-part',
}

async function waitFor(predicate: () => boolean, timeout = 3000): Promise<void> {
  await vi.waitFor(
    () => {
      if (!predicate()) throw new Error('condition not met')
    },
    { timeout, interval: 10 },
  )
}

describe('阈值与拆批', () => {
  it('50MB 走直传，50MB+1 走分片', () => {
    expect(isSmallFile(fakeFile('a.pdf', SMALL_FILE_MAX_SIZE))).toBe(true)
    expect(isSmallFile(fakeFile('b.pdf', SMALL_FILE_MAX_SIZE + 1))).toBe(false)
  })

  it('按 ≤20 个且 ≤50MB 同时拆批', () => {
    const twentyFive = Array.from({ length: 25 }, (_, index) => fakeFile(`f${index}.txt`, 1024))
    const batches = splitIntoBatches(twentyFive)
    expect(batches.map((batch) => batch.length)).toEqual([MAX_BATCH_FILES, 5])

    const big = [
      fakeFile('x1.bin', 20 * 1024 * 1024),
      fakeFile('x2.bin', 20 * 1024 * 1024),
      fakeFile('x3.bin', 20 * 1024 * 1024),
    ]
    expect(splitIntoBatches(big).map((batch) => batch.length)).toEqual([2, 1])
  })

  it('指纹包含名称、大小、最后修改时间，算完哈希后追加哈希', () => {
    const file = fakeFile('a.pdf', 1024, 1700000000000)
    expect(createFingerprint(file)).toBe('a.pdf:1024:1700000000000')
    expect(createFingerprint(file, 'abc')).toBe('a.pdf:1024:1700000000000:abc')
  })
})

describe('UploadEngine 上传状态机', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('小文件直传：HASHING → UPLOADING → COMPLETED，并回传 fileId', async () => {
    const { api, stats } = createFakeApi()
    const completed: FileObject[] = []
    const engine = new UploadEngine({
      api,
      hasher: fakeHasher,
      onCompleted: (_task, file) => completed.push(file),
    })

    const tasks = engine.addFiles([fakeFile('report.pdf', 1024)], 'STUDY_MATERIAL')
    expect(tasks[0]?.status).toBe('WAITING')

    engine.start()
    await waitFor(() => tasks[0]?.status === 'COMPLETED')

    expect(stats.uploadSmall).toBe(1)
    expect(tasks[0]?.fileId).toBe(1001)
    expect(tasks[0]?.progress).toBe(100)
    expect(completed).toHaveLength(1)
    expect(completed[0]?.id).toBe(1001)
  })

  it('51MB 自动分片：11 个分片、并发不超过 3、合并后完成', async () => {
    const { api, stats } = createFakeApi({ partDelayMs: 5 })
    const engine = new UploadEngine({ api, hasher: fakeHasher, concurrency: 3 })

    const tasks = engine.addFiles([fakeFile('big.zip', 51 * 1024 * 1024)], 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks[0]?.status === 'COMPLETED', 5000)

    expect(tasks[0]?.partCount).toBe(11)
    expect(stats.initSession).toBe(1)
    expect(stats.uploadParts.sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11])
    expect(stats.maxActiveParts).toBeLessThanOrEqual(3)
    expect(stats.complete).toBe(1)
    expect(tasks[0]?.fileId).toBe(3000)
    // 完成后本地会话记录清空
    expect(readResumeRecords()).toHaveLength(0)
  })

  it('多个小文件按批上传：25 个文件拆成 20 + 5 两批', async () => {
    const { api, stats } = createFakeApi()
    const engine = new UploadEngine({ api, hasher: fakeHasher })

    const files = Array.from({ length: 25 }, (_, index) => fakeFile(`f${index}.txt`, 1024))
    const tasks = engine.addFiles(files, 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks.every((task) => task.status === 'COMPLETED'), 5000)

    expect(stats.uploadBatch.map((batch) => batch.length)).toEqual([20, 5])
    expect(stats.uploadSmall).toBe(0)
  })

  it('暂停排队任务再继续，仍然完成', async () => {
    const { api } = createFakeApi()
    const engine = new UploadEngine({ api, hasher: fakeHasher })

    const tasks = engine.addFiles([fakeFile('doc.docx', 60 * 1024 * 1024)], 'STUDY_MATERIAL')
    const taskId = tasks[0]?.id ?? ''

    engine.pauseTask(taskId)
    expect(tasks[0]?.status).toBe('PAUSED')

    engine.resumeTask(taskId)
    await waitFor(() => tasks[0]?.status === 'COMPLETED', 5000)
  })

  it('取消上传：状态 CANCELLED 且调用服务端取消会话', async () => {
    const { api, stats } = createFakeApi({ partDelayMs: 30 })
    const engine = new UploadEngine({ api, hasher: fakeHasher })

    const tasks = engine.addFiles([fakeFile('big.iso', 60 * 1024 * 1024)], 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks[0]?.status === 'UPLOADING', 5000)

    const sessionId = tasks[0]?.sessionId
    expect(sessionId).toBeTruthy()
    await engine.cancelTask(tasks[0]?.id ?? '')

    expect(tasks[0]?.status).toBe('CANCELLED')
    expect(stats.cancel).toContain(sessionId)
    expect(readResumeRecords()).toHaveLength(0)
  })

  it('哈希阶段取消：不再创建上传会话', async () => {
    const { api, stats } = createFakeApi()
    let releaseHash: ((hash: string) => void) | undefined
    const hasher = {
      hashFile: () =>
        new Promise<string>((resolve) => {
          releaseHash = resolve
        }),
      hashBlob: async () => 'hash-of-part',
    }
    const engine = new UploadEngine({ api, hasher })

    const tasks = engine.addFiles([fakeFile('big.iso', 60 * 1024 * 1024)], 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks[0]?.status === 'HASHING')

    await engine.cancelTask(tasks[0]?.id ?? '')
    engine.removeTask(tasks[0]?.id ?? '')
    expect(engine.tasks).toHaveLength(0)

    releaseHash?.('hash-of-whole-file')
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(stats.initSession).toBe(0)
    expect(stats.uploadParts).toHaveLength(0)
  })

  it('小文件直传取消：中断请求并保持 CANCELLED', async () => {
    const { api, stats } = createFakeApi({ smallDelayMs: 500 })
    const engine = new UploadEngine({ api, hasher: fakeHasher })

    const tasks = engine.addFiles([fakeFile('small.pdf', 1024)], 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks[0]?.status === 'UPLOADING')

    await engine.cancelTask(tasks[0]?.id ?? '')
    await new Promise((resolve) => setTimeout(resolve, 30))

    expect(stats.uploadSmall).toBe(1)
    expect(tasks[0]?.status).toBe('CANCELLED')
  })

  it('Token 失效（401）：任务失败并提示重新登录', async () => {
    const { api } = createFakeApi({ unauthorized: true })
    const failed: Error[] = []
    const engine = new UploadEngine({
      api,
      hasher: fakeHasher,
      onFailed: (_task, error) => failed.push(error),
    })

    const tasks = engine.addFiles([fakeFile('a.pdf', 1024)], 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks[0]?.status === 'FAILED')

    expect(tasks[0]?.error).toContain('登录已失效')
    expect(failed).toHaveLength(1)
  })

  it('分片 503 自动重试：前两次失败后成功，最终完成', async () => {
    const { api, stats } = createFakeApi({ transientPartFailures: 2 })
    const engine = new UploadEngine({
      api,
      hasher: fakeHasher,
      retryDelays: [1, 1, 1],
    })

    const tasks = engine.addFiles([fakeFile('retry.bin', 60 * 1024 * 1024)], 'STUDY_MATERIAL')
    engine.start()
    await waitFor(() => tasks[0]?.status === 'COMPLETED', 5000)

    // 第 1 片失败两次后被第 3 次尝试拿下
    expect(stats.uploadParts.filter((part) => part === 1)).toHaveLength(1)
    expect(tasks[0]?.status).toBe('COMPLETED')
  })

  it('服务端已有 71/72 片时，只补第 72 片并等待确认后合并', async () => {
    const partCount = 72
    const uploaded = Array.from({ length: 71 }, (_, index) => index + 1)
    const { api, stats } = createFakeApi({ initialUploadedParts: uploaded })
    const engine = new UploadEngine({ api, hasher: fakeHasher, concurrency: 3 })

    const tasks = engine.addFiles(
      [fakeFile('large.pdf', DEFAULT_PART_SIZE * partCount)],
      'STUDY_MATERIAL',
    )
    engine.start()
    await waitFor(() => tasks[0]?.status === 'COMPLETED', 5000)

    expect(stats.uploadParts).toEqual([72])
    expect(stats.complete).toBe(1)
    expect(tasks[0]?.uploadedParts).toHaveLength(partCount)
  })

  it('单片重试耗尽后继续上传其他片，回查缺片并补传成功', async () => {
    const partCount = 72
    const { api, stats } = createFakeApi({
      partFailuresByNumber: { [partCount]: MAX_PART_RETRIES + 1 },
    })
    const engine = new UploadEngine({
      api,
      hasher: fakeHasher,
      concurrency: 6,
      retryDelays: [1, 1, 1],
    })

    const tasks = engine.addFiles(
      [fakeFile('almost-there.pdf', DEFAULT_PART_SIZE * partCount)],
      'STUDY_MATERIAL',
    )
    engine.start()
    await waitFor(() => tasks[0]?.status === 'COMPLETED', 5000)

    expect(stats.uploadPartAttempts.filter((part) => part === partCount)).toHaveLength(
      MAX_PART_RETRIES + 2,
    )
    expect(stats.complete).toBe(1)
    expect(tasks[0]?.uploadedParts).toHaveLength(partCount)
  })

  it('本地续传记录：损坏 JSON 时安全降级，可按 sessionId 删除', () => {
    localStorage.setItem(RESUME_STORAGE_KEY, '{not-json')
    expect(readResumeRecords()).toEqual([])

    localStorage.setItem(
      RESUME_STORAGE_KEY,
      JSON.stringify([
        {
          fingerprint: 'a.pdf:1024:1700000000000',
          sessionId: 42,
          scene: 'STUDY_MATERIAL',
          fileName: 'a.pdf',
          fileSize: 1024,
          lastModified: 1700000000000,
          status: 'UPLOADING',
          partSize: DEFAULT_PART_SIZE,
          partCount: 1,
          uploadedParts: [],
          updatedAt: 1,
        },
      ]),
    )
    expect(readResumeRecords()).toHaveLength(1)
    removeResumeRecord(42)
    expect(readResumeRecords()).toHaveLength(0)
  })
})
