/**
 * 组件懒加载的状态封装。
 *
 * 与 `defineAsyncComponent` 的区别：这里把「加载 / 失败 / 重试」暴露成显式状态，
 * 页面可以直接用占位、错误提示和重试按钮渲染，而不是依赖组件内部的默认行为。
 *
 * 约定：
 * - `idle`：还没触发加载；页面首次渲染时不应发请求。
 * - `loading`：正在拉取 chunk；并发调用会复用同一个 Promise，避免重复请求。
 * - `ready`：组件已就绪，可以挂载。
 * - `error`：加载失败；`error` 里保留原因，`retry()` 会重新发起一次加载。
 */

import { markRaw, ref, shallowRef, type Component, type Ref } from 'vue'

export type LazyStatus = 'idle' | 'loading' | 'ready' | 'error'

export interface LazyComponentState {
  /** 加载完成后的组件；未就绪时为 null */
  component: Ref<Component | null>
  status: Ref<LazyStatus>
  error: Ref<Error | null>
  /** 触发加载；已就绪时直接返回 true */
  load: () => Promise<boolean>
  /** 预加载：只发起加载，不等待结果，失败不抛出 */
  preload: () => void
  /** 失败后重试；与 load 的区别只是语义 */
  retry: () => Promise<boolean>
  /** 重置状态，仅用于测试或组件重建 */
  reset: () => void
}

type LoaderResult<T> = { default?: T } | T

/** 兼容 `() => import('...')` 返回的模块对象与直接返回组件两种写法 */
function resolveComponent<T extends Component>(module: LoaderResult<T>): T {
  if (module && typeof module === 'object' && 'default' in module && module.default) {
    return module.default as T
  }
  return module as T
}

export function createLazyComponent<T extends Component>(
  loader: () => Promise<LoaderResult<T>>,
): LazyComponentState {
  const component = shallowRef<Component | null>(null) as Ref<Component | null>
  const status = ref<LazyStatus>('idle')
  const error = ref<Error | null>(null)

  let pending: Promise<boolean> | null = null

  function load(): Promise<boolean> {
    if (component.value) {
      status.value = 'ready'
      return Promise.resolve(true)
    }
    if (pending) return pending

    status.value = 'loading'
    error.value = null

    // 同步发起请求：预加载（hover/focus）能立即触发 chunk 下载，而不是等一个微任务
    let request: Promise<LoaderResult<T>>
    try {
      request = Promise.resolve(loader())
    } catch (reason) {
      request = Promise.reject(reason)
    }

    pending = request
      .then((module) => {
        // markRaw：组件对象不需要响应式代理，避免无意义的深度劫持
        component.value = markRaw(resolveComponent(module)) as Component
        status.value = 'ready'
        return true
      })
      .catch((reason: unknown) => {
        error.value = reason instanceof Error ? reason : new Error(String(reason))
        status.value = 'error'
        return false
      })
      .finally(() => {
        pending = null
      })

    return pending
  }

  function preload(): void {
    // 预加载失败不影响后续点击：失败后 status 为 error，点击仍会走 load() 重新请求
    void load()
  }

  function retry(): Promise<boolean> {
    if (component.value) {
      status.value = 'ready'
      return Promise.resolve(true)
    }
    return load()
  }

  function reset(): void {
    pending = null
    component.value = null
    status.value = 'idle'
    error.value = null
  }

  return { component, status, error, load, preload, retry, reset }
}
