/**
 * B：大文件 SHA-256 哈希 Worker
 *
 * 设计说明：
 * - 整文件哈希用增量方式（4MB 分块流式读），不把大文件整体读进内存；
 * - 分片哈希优先用 `crypto.subtle.digest('SHA-256', ...)`，非安全上下文（如局域网 http）
 *   `crypto.subtle` 不可用时自动回落到本文件的纯 JS 实现，保证联调环境也能算；
 * - 全程在 Worker 线程，避免大文件计算阻塞页面。
 *
 * 消息协议见 sender：`uploader.ts` 里的 HashService。
 */

/** 消息协议：主线程 -> Worker */
export type HashWorkerRequest =
  | { type: 'file'; id: string; file: File }
  | { type: 'blob'; id: string; blob: Blob }

/** 消息协议：Worker -> 主线程 */
export type HashWorkerResponse =
  | { type: 'progress'; id: string; loaded: number; total: number }
  | { type: 'done'; id: string; hash: string }
  | { type: 'error'; id: string; message: string }

/** 只声明用到的 Worker 全局能力，避免 dom 与 webworker 两套 lib 冲突 */
interface WorkerScope {
  postMessage(message: HashWorkerResponse): void
  onmessage: ((event: MessageEvent<HashWorkerRequest>) => void) | null
}

const scope = self as unknown as WorkerScope

/** SHA-256 轮常量 */
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
])

function rotr(value: number, bits: number): number {
  return ((value >>> bits) | (value << (32 - bits))) >>> 0
}

/** 把 64 字节块展开成 16 个 32 位字 + 消息扩展 */
function loadBlock(bytes: Uint8Array, offset: number, w: Uint32Array): void {
  for (let i = 0; i < 16; i++) {
    const j = offset + i * 4
    w[i] = (((bytes[j] ?? 0) << 24) | ((bytes[j + 1] ?? 0) << 16) | ((bytes[j + 2] ?? 0) << 8) | (bytes[j + 3] ?? 0)) >>> 0
  }
  for (let i = 16; i < 64; i++) {
    const w15 = w[i - 15] ?? 0
    const w2 = w[i - 2] ?? 0
    const s0 = (rotr(w15, 7) ^ rotr(w15, 18) ^ (w15 >>> 3)) >>> 0
    const s1 = (rotr(w2, 17) ^ rotr(w2, 19) ^ (w2 >>> 10)) >>> 0
    w[i] = ((w[i - 16] ?? 0) + s0 + (w[i - 7] ?? 0) + s1) >>> 0
  }
}

/** 一轮压缩函数 */
function compress(state: Uint32Array, w: Uint32Array): void {
  let a = state[0] ?? 0
  let b = state[1] ?? 0
  let c = state[2] ?? 0
  let d = state[3] ?? 0
  let e = state[4] ?? 0
  let f = state[5] ?? 0
  let g = state[6] ?? 0
  let h = state[7] ?? 0

  for (let i = 0; i < 64; i++) {
    const t1 =
      (h + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + ((e & f) ^ (~e & g)) + (K[i] ?? 0) + (w[i] ?? 0)) >>> 0
    const t2 = ((rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0
    h = g
    g = f
    f = e
    e = (d + t1) >>> 0
    d = c
    c = b
    b = a
    a = (t1 + t2) >>> 0
  }

  state[0] = ((state[0] ?? 0) + a) >>> 0
  state[1] = ((state[1] ?? 0) + b) >>> 0
  state[2] = ((state[2] ?? 0) + c) >>> 0
  state[3] = ((state[3] ?? 0) + d) >>> 0
  state[4] = ((state[4] ?? 0) + e) >>> 0
  state[5] = ((state[5] ?? 0) + f) >>> 0
  state[6] = ((state[6] ?? 0) + g) >>> 0
  state[7] = ((state[7] ?? 0) + h) >>> 0
}

/** 增量 SHA-256（纯 JS，不依赖 crypto.subtle） */
class Sha256 {
  private readonly state = new Uint32Array([
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
  ])

  private readonly w = new Uint32Array(64)

  private readonly buffer = new Uint8Array(64)

  private bufferLength = 0

  private bytesHashed = 0

  update(bytes: Uint8Array): void {
    this.bytesHashed += bytes.length
    let offset = 0

    if (this.bufferLength > 0) {
      while (this.bufferLength < 64 && offset < bytes.length) {
        this.buffer[this.bufferLength++] = bytes[offset++] ?? 0
      }
      if (this.bufferLength === 64) {
        loadBlock(this.buffer, 0, this.w)
        compress(this.state, this.w)
        this.bufferLength = 0
      }
    }

    const fullBlocks = (bytes.length - offset) >> 6
    for (let i = 0; i < fullBlocks; i++) {
      loadBlock(bytes, offset, this.w)
      compress(this.state, this.w)
      offset += 64
    }

    while (offset < bytes.length) {
      this.buffer[this.bufferLength++] = bytes[offset++] ?? 0
    }
  }

  /** 收尾并输出 64 位小写十六进制；单次实例只调用一次 */
  digest(): string {
    const tail = new Uint8Array(64)
    tail.set(this.buffer.subarray(0, this.bufferLength), 0)
    let index = this.bufferLength
    tail[index++] = 0x80

    if (index > 56) {
      while (index < 64) tail[index++] = 0
      loadBlock(tail, 0, this.w)
      compress(this.state, this.w)
      tail.fill(0)
      index = 0
    }
    while (index < 56) tail[index++] = 0

    // 原始字节数 * 8 -> 64 位大端长度
    const bits = this.bytesHashed * 8
    const high = Math.floor(bits / 0x100000000)
    const low = bits % 0x100000000
    tail[56] = (high >>> 24) & 0xff
    tail[57] = (high >>> 16) & 0xff
    tail[58] = (high >>> 8) & 0xff
    tail[59] = high & 0xff
    tail[60] = (low >>> 24) & 0xff
    tail[61] = (low >>> 16) & 0xff
    tail[62] = (low >>> 8) & 0xff
    tail[63] = low & 0xff

    loadBlock(tail, 0, this.w)
    compress(this.state, this.w)

    let hex = ''
    for (let i = 0; i < 8; i++) {
      hex += ((this.state[i] ?? 0) >>> 0).toString(16).padStart(8, '0')
    }
    return hex
  }
}

function hexOf(bytes: Uint8Array): string {
  let hex = ''
  for (const byte of bytes) {
    hex += byte.toString(16).padStart(2, '0')
  }
  return hex
}

/** 小块优先走 WebCrypto，不可用（非安全上下文）则回落纯 JS */
async function sha256OfBytes(bytes: Uint8Array): Promise<string> {
  const subtle = typeof crypto !== 'undefined' ? crypto.subtle : undefined
  if (subtle) {
    try {
      const digest = await subtle.digest('SHA-256', bytes.buffer as ArrayBuffer)
      return hexOf(new Uint8Array(digest))
    } catch {
      // 非安全上下文 / 浏览器限制，走下面的纯 JS
    }
  }
  const sha = new Sha256()
  sha.update(bytes)
  return sha.digest()
}

/** 整文件：4MB 分块增量哈希，边算边回报进度 */
async function hashWholeFile(id: string, file: File): Promise<void> {
  const sha = new Sha256()
  const chunkSize = 4 * 1024 * 1024
  let offset = 0
  while (offset < file.size) {
    const end = Math.min(offset + chunkSize, file.size)
    const buffer = await file.slice(offset, end).arrayBuffer()
    sha.update(new Uint8Array(buffer))
    offset = end
    scope.postMessage({ type: 'progress', id, loaded: offset, total: file.size })
  }
  scope.postMessage({ type: 'done', id, hash: sha.digest() })
}

async function hashBlob(id: string, blob: Blob): Promise<void> {
  const buffer = await blob.arrayBuffer()
  scope.postMessage({ type: 'done', id, hash: await sha256OfBytes(new Uint8Array(buffer)) })
}

async function handle(request: HashWorkerRequest): Promise<void> {
  try {
    if (request.type === 'file') {
      await hashWholeFile(request.id, request.file)
      return
    }
    await hashBlob(request.id, request.blob)
  } catch (error) {
    scope.postMessage({
      type: 'error',
      id: request.id,
      message: (error as Error)?.message ?? '哈希计算失败',
    })
  }
}

scope.onmessage = (event: MessageEvent<HashWorkerRequest>) => {
  const request = event.data
  if (!request || !request.id) return
  void handle(request)
}
