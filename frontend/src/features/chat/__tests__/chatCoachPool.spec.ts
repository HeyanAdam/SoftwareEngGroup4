/**
 * 聊天页导学建议候选池：纯函数单测
 *
 * 覆盖三条交互口径：
 * 1. 一屏只显示 3 条，池子不足 3 条时有几条显示几条；
 * 2. "换一批"在池内轮换、走到末尾回卷，不会把整栏清空；
 * 3. "暂不需要"后补货只并入没见过、没被忽略过的建议。
 */

import { describe, expect, it } from 'vitest'
import { advanceCursor, computeWindow, mergeFreshPool } from '../chatCoach'

const items = (count: number): Array<{ id: number }> =>
  Array.from({ length: count }, (_, index) => ({ id: 901 + index }))

describe('computeWindow', () => {
  it('池子 9 条时每屏 3 条', () => {
    expect(computeWindow(items(9), 0, 3).map((item) => item.id)).toEqual([901, 902, 903])
    expect(computeWindow(items(9), 3, 3).map((item) => item.id)).toEqual([904, 905, 906])
  })

  it('游标超出末尾自动回卷，不会返回空', () => {
    expect(computeWindow(items(9), 9, 3).map((item) => item.id)).toEqual([901, 902, 903])
    expect(computeWindow(items(4), 3, 3).map((item) => item.id)).toEqual([904, 901, 902])
  })

  it('池子只有 1~2 条时显示实际条数，不报错', () => {
    expect(computeWindow(items(2), 0, 3).map((item) => item.id)).toEqual([901, 902])
    expect(computeWindow([], 0, 3)).toEqual([])
  })
})

describe('advanceCursor', () => {
  it('每点一次换一批前进一屏，走到末尾回到起点', () => {
    expect(advanceCursor(0, 9, 3)).toBe(3)
    expect(advanceCursor(3, 9, 3)).toBe(6)
    expect(advanceCursor(6, 9, 3)).toBe(0)
  })

  it('池子不足一屏时保持起点，不清空列表', () => {
    expect(advanceCursor(0, 3, 3)).toBe(0)
    expect(advanceCursor(0, 0, 3)).toBe(0)
  })
})

describe('mergeFreshPool', () => {
  it('只并入没见过也没被忽略的候选', () => {
    const pool = [{ id: 901 }, { id: 902 }]
    const incoming = [{ id: 901 }, { id: 902 }, { id: 903 }, { id: 904 }]
    const merged = mergeFreshPool(pool, incoming, [903])
    expect(merged.map((item) => item.id)).toEqual([901, 902, 904])
  })

  it('没有新候选时返回原池，不重复追加', () => {
    const pool = [{ id: 901 }, { id: 902 }]
    expect(mergeFreshPool(pool, [{ id: 901 }], []).map((item) => item.id)).toEqual([901, 902])
  })

  it('补货后忽略项不会被重新放回', () => {
    const pool: Array<{ id: number }> = []
    expect(mergeFreshPool(pool, [{ id: 901 }], [901])).toEqual([])
  })
})
