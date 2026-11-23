/**
 * 组件懒加载状态机测试：
 * 初始不请求、成功就绪、失败保留原因、重试可恢复、并发只发一次请求。
 */

import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createLazyComponent } from './lazyComponent'

const Dummy = defineComponent({
  name: 'DummyChart',
  render: () => h('div', 'dummy'),
})

/** 可控 Promise：用于验证并发调用只发一次请求 */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('createLazyComponent', () => {
  it('初始状态为 idle，且不触发 loader', () => {
    const loader = vi.fn().mockResolvedValue({ default: Dummy })
    const state = createLazyComponent(loader)

    expect(state.status.value).toBe('idle')
    expect(state.component.value).toBeNull()
    expect(loader).not.toHaveBeenCalled()
  })

  it('load 成功后进入 ready，并取到模块的 default 组件', async () => {
    const loader = vi.fn().mockResolvedValue({ default: Dummy })
    const state = createLazyComponent(loader)

    const ok = await state.load()

    expect(ok).toBe(true)
    expect(state.status.value).toBe('ready')
    expect(state.component.value).toBe(Dummy)
    expect(loader).toHaveBeenCalledTimes(1)
  })

  it('并发 load 复用同一个请求', async () => {
    const gate = deferred<{ default: typeof Dummy }>()
    const loader = vi.fn().mockReturnValue(gate.promise)
    const state = createLazyComponent(loader)

    const first = state.load()
    const second = state.load()
    expect(state.status.value).toBe('loading')
    expect(loader).toHaveBeenCalledTimes(1)

    gate.resolve({ default: Dummy })
    await expect(Promise.all([first, second])).resolves.toEqual([true, true])
    expect(state.status.value).toBe('ready')
  })

  it('load 失败进入 error，并保留失败原因，父页面不被打断', async () => {
    const loader = vi.fn().mockRejectedValue(new Error('chunk 404'))
    const state = createLazyComponent(loader)

    const ok = await state.load()

    expect(ok).toBe(false)
    expect(state.status.value).toBe('error')
    expect(state.error.value?.message).toBe('chunk 404')
    expect(state.component.value).toBeNull()
  })

  it('失败后 retry 能重新发起加载并恢复', async () => {
    const loader = vi
      .fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ default: Dummy })
    const state = createLazyComponent(loader)

    expect(await state.load()).toBe(false)
    expect(state.status.value).toBe('error')

    expect(await state.retry()).toBe(true)
    expect(state.status.value).toBe('ready')
    expect(state.error.value).toBeNull()
    expect(loader).toHaveBeenCalledTimes(2)
  })

  it('预加载失败不抛出，点击后的 load 仍能重新请求成功', async () => {
    const loader = vi
      .fn()
      .mockRejectedValueOnce(new Error('preload failed'))
      .mockResolvedValueOnce({ default: Dummy })
    const state = createLazyComponent(loader)

    expect(() => state.preload()).not.toThrow()
    await vi.waitFor(() => expect(state.status.value).toBe('error'))

    expect(await state.load()).toBe(true)
    expect(state.status.value).toBe('ready')
    expect(loader).toHaveBeenCalledTimes(2)
  })

  it('reset 回到 idle，便于组件重建后重新加载', async () => {
    const loader = vi.fn().mockResolvedValue({ default: Dummy })
    const state = createLazyComponent(loader)

    await state.load()
    state.reset()

    expect(state.status.value).toBe('idle')
    expect(state.component.value).toBeNull()
  })
})
