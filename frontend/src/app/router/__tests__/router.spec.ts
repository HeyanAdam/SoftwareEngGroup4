/**
 * 路由懒加载审计（成员 1 任务五）
 *
 * 断言所有带组件的路由都是 `() => import(...)` 形态，
 * 即登录页只下载登录页代码，聊天 / 后台业务页面按访问才加载。
 */

import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from '../index'

function flatten(records: RouteRecordRaw[], acc: RouteRecordRaw[] = []): RouteRecordRaw[] {
  records.forEach((record) => {
    acc.push(record)
    if (record.children) flatten(record.children, acc)
  })
  return acc
}

const allRoutes = flatten(routes)

describe('路由级代码拆分', () => {
  it('所有页面组件都使用动态导入（function），不存在静态导入的业务页面', () => {
    const withComponent = allRoutes.filter((route) => route.component !== undefined)
    expect(withComponent.length).toBeGreaterThan(10)
    withComponent.forEach((route) => {
      expect(typeof route.component).toBe('function')
    })
  })

  it('登录 / 注册 / 找回密码与错误页齐全', () => {
    const names = new Set(allRoutes.map((route) => route.name).filter(Boolean))
    ;[
      'Login',
      'Register',
      'ForgotPassword',
      'Unauthorized',
      'Forbidden',
      'TooManyRequests',
      'NotFound',
    ].forEach((name) => expect(names.has(name)).toBe(true))
  })

  it('学生端与后台业务页面均为独立懒加载路由', () => {
    const names = new Set(allRoutes.map((route) => route.name).filter(Boolean))
    ;[
      'Dashboard',
      'StudyPlan',
      'File',
      'Chat',
      'AdminLogin',
      'AdminDashboard',
      'AdminUsers',
      'AdminRoles',
      'AdminPermissions',
      'AdminLogs',
    ].forEach((name) => expect(names.has(name)).toBe(true))
  })

  it('保留 404 兜底路由', () => {
    expect(allRoutes.some((route) => route.path === '/:pathMatch(.*)*')).toBe(true)
  })
})
