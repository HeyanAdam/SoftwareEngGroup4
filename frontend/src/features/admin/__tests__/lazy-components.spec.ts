/**
 * 结构约束测试：目标组件必须保持「动态 import」，
 * 防止后续有人把 import 改回静态写法，把懒加载悄悄退化掉。
 *
 * 通过 `?raw` 读取页面源码，用文本断言守住三件事：
 * 1. 目标组件不再静态导入；
 * 2. 存在动态 import 与加载调用；
 * 3. 只读权限账号不会触发加载，首屏必要组件仍保持静态导入。
 */

import { describe, expect, it } from 'vitest'
import usersPageRaw from '../users/AdminUsersPage.vue?raw'
import rolesPageRaw from '../roles/AdminRolesPage.vue?raw'
import dashboardPageRaw from '../dashboard/AdminDashboardPage.vue?raw'
import lazyGateRaw from '../perf/LazyGate.vue?raw'

describe('目标组件使用动态 import', () => {
  it('用户管理页：角色抽屉不再静态导入，改为按需加载 + 悬停/聚焦预加载', () => {
    expect(usersPageRaw).not.toMatch(/import\s+AssignRoleDrawer\s+from/)
    expect(usersPageRaw).toContain("import('./components/AssignRoleDrawer.vue')")
    expect(usersPageRaw).toContain('createLazyComponent')
    expect(usersPageRaw).toContain('void roleDrawer.load()')
    expect(usersPageRaw).toContain('roleDrawer.preload()')
    expect(usersPageRaw).toContain('@mouseenter="preloadRoleDrawer"')
    expect(usersPageRaw).toContain('@focus="preloadRoleDrawer"')
  })

  it('角色管理页：表单弹窗不再静态导入，改为打开时加载', () => {
    expect(rolesPageRaw).not.toMatch(/import\s+RoleFormDialog\s+from/)
    expect(rolesPageRaw).toContain("import('./components/RoleFormDialog.vue')")
    expect(rolesPageRaw).toContain('createLazyComponent')
    expect(rolesPageRaw).toContain('void roleFormDialog.load()')
  })

  it('管理大屏：图表不再静态导入，进入页面即请求（首屏需要，不等交互）', () => {
    expect(dashboardPageRaw).not.toMatch(/import\s+DashboardTrendChart\s+from/)
    expect(dashboardPageRaw).toContain("import('./components/DashboardTrendChart.vue')")
    expect(dashboardPageRaw).toContain('void trendChart.load()')
  })
})

describe('权限与首屏边界', () => {
  it('只读账号不打开抽屉、不触发加载', () => {
    expect(usersPageRaw).toContain('if (!canUpdate.value) return')
    expect(usersPageRaw).toContain('v-if="canUpdate"')
  })

  it('只读账号不打开表单弹窗、不触发加载', () => {
    expect(rolesPageRaw).toContain('if (!canUpdate.value) return')
  })

  it('首屏必要组件保持静态导入：页头、角色列表、权限矩阵不在懒加载名单', () => {
    expect(usersPageRaw).toContain(
      "import AdminPageHeader from '../layout/components/AdminPageHeader.vue'",
    )
    expect(rolesPageRaw).toContain("import RoleListPanel from './components/RoleListPanel.vue'")
    expect(rolesPageRaw).toContain("import PermissionMatrix from './components/PermissionMatrix.vue'")
    expect(dashboardPageRaw).toContain("import DashboardKpiCard from './components/DashboardKpiCard.vue'")
  })

  it('占位尺寸与失败重试入口存在', () => {
    expect(lazyGateRaw).toContain('minHeight')
    expect(lazyGateRaw).toContain('重试')
    expect(lazyGateRaw).toContain('state.retry()')
  })
})
