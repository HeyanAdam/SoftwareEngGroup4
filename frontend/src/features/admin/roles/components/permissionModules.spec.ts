/**
 * 权限分组纯函数测试：20 条权限码必须全部落在正确模块，
 * 分组不依赖 sort 区间，全选/半选与 code→id 映射符合后端不对称接口。
 */

import { describe, expect, it } from 'vitest'
import {
  MODULE_ORDER,
  groupPermissions,
  groupSelectionState,
  isSameSelection,
  moduleOfPermission,
  permissionCodesToIds,
  toggleGroup,
  unmatchedPermissionCodes,
  type PermissionLike,
} from './permissionModules'
import { isBuiltinRole, isSystemAdminRole } from './roleMeta'

/**
 * 与后端 PermissionConstants.CATALOG 一致的 20 条权限码。
 * sort 故意使用「旧库交叉排序」：kb:view=10、file:view=20、chat:send=60、admin:*=70~110，
 * 如果用 sort 区间分组，这些权限会被错误地混到同一组。
 */
const CATALOG: Array<PermissionLike & { permName: string }> = [
  { id: 1, permCode: 'study-plan:view', permName: '查看学习计划', sort: 1 },
  { id: 2, permCode: 'study-plan:create', permName: '生成学习计划', sort: 2 },
  { id: 3, permCode: 'study-plan:update', permName: '调整学习计划', sort: 3 },
  { id: 4, permCode: 'study-task:update', permName: '更新任务状态', sort: 4 },
  { id: 5, permCode: 'question:view', permName: '浏览题库', sort: 10 },
  { id: 6, permCode: 'file:view', permName: '文件列表', sort: 20 },
  { id: 7, permCode: 'file:upload', permName: '上传文件', sort: 21 },
  { id: 8, permCode: 'file:download', permName: '下载文件', sort: 22 },
  { id: 9, permCode: 'file:delete', permName: '删除文件', sort: 23 },
  { id: 10, permCode: 'kb:view', permName: '查看知识库', sort: 10 },
  { id: 11, permCode: 'kb:create', permName: '创建知识库', sort: 20 },
  { id: 12, permCode: 'kb:delete', permName: '删除知识库', sort: 30 },
  { id: 13, permCode: 'doc:upload', permName: '上传文档', sort: 40 },
  { id: 14, permCode: 'doc:delete', permName: '删除文档', sort: 50 },
  { id: 15, permCode: 'chat:send', permName: '智能问答', sort: 60 },
  { id: 16, permCode: 'admin:user:list', permName: '用户列表', sort: 70 },
  { id: 17, permCode: 'admin:user:update', permName: '用户管理', sort: 80 },
  { id: 18, permCode: 'admin:role:list', permName: '角色列表', sort: 90 },
  { id: 19, permCode: 'admin:role:update', permName: '角色权限管理', sort: 100 },
  { id: 20, permCode: 'admin:vector:manage', permName: '向量库管理', sort: 110 },
]

const EXPECTED_MODULE: Record<string, string> = {
  'study-plan:view': '督学陪伴',
  'study-plan:create': '督学陪伴',
  'study-plan:update': '督学陪伴',
  'study-task:update': '督学陪伴',
  'question:view': '刷题训练',
  'file:view': '文件存储',
  'file:upload': '文件存储',
  'file:download': '文件存储',
  'file:delete': '文件存储',
  'kb:view': '知识库',
  'kb:create': '知识库',
  'kb:delete': '知识库',
  'doc:upload': '知识库',
  'doc:delete': '知识库',
  'chat:send': 'AI 对话',
  'admin:user:list': '后台管理',
  'admin:user:update': '后台管理',
  'admin:role:list': '后台管理',
  'admin:role:update': '后台管理',
  'admin:vector:manage': '后台管理',
}

describe('权限码 → 模块', () => {
  it('20 条权限码全部分到正确模块', () => {
    CATALOG.forEach((permission) => {
      expect(moduleOfPermission(permission.permCode)).toBe(EXPECTED_MODULE[permission.permCode])
    })
  })

  it('admin:* 多级码不落入「其他权限」', () => {
    CATALOG.filter((permission) => permission.permCode.startsWith('admin:'))
      .forEach((permission) => {
        expect(moduleOfPermission(permission.permCode)).toBe('后台管理')
      })
  })

  it('未知前缀与空码降级为「其他权限」', () => {
    expect(moduleOfPermission('report:export')).toBe('其他权限')
    expect(moduleOfPermission('')).toBe('其他权限')
  })
})

describe('按前缀分组', () => {
  it('模块顺序稳定，模块内按 sort/id 排序', () => {
    // 打乱输入顺序，分组结果仍按固定模块顺序
    const shuffled = [...CATALOG].reverse()
    const groups = groupPermissions(shuffled)

    expect(groups.map((group) => group.module)).toEqual([
      '督学陪伴',
      '刷题训练',
      '文件存储',
      '知识库',
      'AI 对话',
      '后台管理',
    ])
    expect(groups.map((group) => group.permissions.length)).toEqual([4, 1, 4, 5, 1, 5])
    expect(groups.map((group) => group.permissions[0].permCode)).toEqual([
      'study-plan:view',
      'question:view',
      'file:view',
      'kb:view',
      'chat:send',
      'admin:user:list',
    ])
    expect(groupPermissions(shuffled).reduce((sum, group) => sum + group.permissions.length, 0)).toBe(20)
  })

  it('不按 sort 区间分组：sort=10 的 kb:view 与 file:* 不在同一模块', () => {
    const groups = groupPermissions(CATALOG)
    const kbGroup = groups.find((group) => group.module === '知识库')
    const fileGroup = groups.find((group) => group.module === '文件存储')

    expect(kbGroup?.permissions.map((p) => p.permCode)).toEqual([
      'kb:view',
      'kb:create',
      'kb:delete',
      'doc:upload',
      'doc:delete',
    ])
    expect(fileGroup?.permissions.every((p) => p.permCode.startsWith('file:'))).toBe(true)
  })

  it('没有权限时不返回空模块卡片', () => {
    expect(groupPermissions([])).toEqual([])
  })
})

describe('全选与半选', () => {
  const fileGroup = CATALOG.filter((permission) => permission.permCode.startsWith('file:'))

  it('未选择：不全选也不半选', () => {
    expect(groupSelectionState([], fileGroup)).toEqual({
      total: 4,
      selected: 0,
      allSelected: false,
      indeterminate: false,
    })
  })

  it('部分选择：半选', () => {
    expect(groupSelectionState([6, 7], fileGroup)).toEqual({
      total: 4,
      selected: 2,
      allSelected: false,
      indeterminate: true,
    })
  })

  it('全部选择：全选且不半选', () => {
    expect(groupSelectionState([6, 7, 8, 9], fileGroup)).toEqual({
      total: 4,
      selected: 4,
      allSelected: true,
      indeterminate: false,
    })
  })

  it('空分组不判定为全选', () => {
    expect(groupSelectionState([], []).allSelected).toBe(false)
  })
})

describe('模块全选切换', () => {
  const fileGroup = CATALOG.filter((permission) => permission.permCode.startsWith('file:'))

  it('半选状态点全选 → 补齐本组，保留组外勾选', () => {
    expect(toggleGroup([1, 6, 10], fileGroup)).toEqual([1, 6, 7, 8, 9, 10])
  })

  it('全选状态点全选 → 取消本组，保留组外勾选', () => {
    expect(toggleGroup([1, 6, 7, 8, 9, 10], fileGroup)).toEqual([1, 10])
  })

  it('返回结果始终升序', () => {
    expect(toggleGroup([9, 1], fileGroup)).toEqual([1, 6, 7, 8, 9])
  })
})

describe('权限码 → 权限 ID', () => {
  it('按权限字典映射为后端要求的 id 数组', () => {
    expect(
      permissionCodesToIds(['admin:role:list', 'file:view', 'chat:send'], CATALOG),
    ).toEqual([6, 15, 18])
  })

  it('未知权限码被忽略并可由调用方提示', () => {
    expect(permissionCodesToIds(['file:view', 'kb:veiw'], CATALOG)).toEqual([6])
    expect(unmatchedPermissionCodes(['file:view', 'kb:veiw'], CATALOG)).toEqual(['kb:veiw'])
  })

  it('重复权限码去重', () => {
    expect(permissionCodesToIds(['file:view', 'file:view'], CATALOG)).toEqual([6])
  })
})

describe('未保存判定', () => {
  it('顺序无关、长度敏感', () => {
    expect(isSameSelection([6, 7], [7, 6])).toBe(true)
    expect(isSameSelection([6], [6, 7])).toBe(false)
    expect(isSameSelection([], [])).toBe(true)
  })
})

describe('角色元信息', () => {
  it('4 个官方内置角色，旧角色码不算内置', () => {
    expect(isBuiltinRole('SYSTEM_ADMIN')).toBe(true)
    expect(isBuiltinRole('DATA_AUDITOR')).toBe(true)
    expect(isBuiltinRole('ADMIN')).toBe(false)
    expect(isBuiltinRole('DATA_VIEWER')).toBe(false)
  })

  it('只有 SYSTEM_ADMIN 走全权只读规则', () => {
    expect(isSystemAdminRole('SYSTEM_ADMIN')).toBe(true)
    expect(isSystemAdminRole('DATA_AUDITOR')).toBe(false)
  })

  it('模块顺序与后端权限码清单一致', () => {
    expect(MODULE_ORDER).toEqual([
      '督学陪伴',
      '刷题训练',
      '文件存储',
      '知识库',
      'AI 对话',
      '后台管理',
      '其他权限',
    ])
  })
})
