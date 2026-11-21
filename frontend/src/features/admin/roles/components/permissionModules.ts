/**
 * 权限分组的纯函数工具（不含 Vue 依赖，可直接单测）。
 *
 * 分组依据是权限码前缀，不是 sort 数字区间：
 * 数据库里存在旧排序与交叉排序（例如 file:* 的 20~23 与 kb:view 的 10 交叉），
 * 按 sort 切区间会把不同模块混在一起。
 */

/** 模块稳定展示顺序：与权限码清单一致，未登记前缀统一落「其他权限」 */
export const MODULE_ORDER = [
  '督学陪伴',
  '刷题训练',
  '文件存储',
  '知识库',
  'AI 对话',
  '后台管理',
  '其他权限',
] as const

export type PermissionModule = (typeof MODULE_ORDER)[number]

/** 权限码前缀 → 模块名 */
const MODULE_BY_PREFIX: Record<string, PermissionModule> = {
  'study-plan': '督学陪伴',
  'study-task': '督学陪伴',
  question: '刷题训练',
  file: '文件存储',
  kb: '知识库',
  doc: '知识库',
  chat: 'AI 对话',
  admin: '后台管理',
}

/** 取权限码所属模块：只看第一段前缀，多级码（admin:user:list）同样归「后台管理」 */
export function moduleOfPermission(code: string): PermissionModule {
  const prefix = String(code ?? '')
    .trim()
    .split(':')[0]
    .toLowerCase()
  return MODULE_BY_PREFIX[prefix] ?? '其他权限'
}

/** 分组所需的最小字段：权限字典行、角色权限都满足 */
export interface PermissionLike {
  id: number
  permCode: string
  sort?: number
}

export interface PermissionGroup<T extends PermissionLike = PermissionLike> {
  module: PermissionModule
  permissions: T[]
}

/** 同模块内按 sort、再按 id 排序，保证刷新后顺序稳定 */
function comparePermission(a: PermissionLike, b: PermissionLike): number {
  const sortA = a.sort ?? 0
  const sortB = b.sort ?? 0
  return sortA - sortB || a.id - b.id
}

/**
 * 按模块分组，模块顺序固定为 MODULE_ORDER，模块内按 sort/id 排序。
 * 只返回实际有权限的模块，不渲染空卡片。
 */
export function groupPermissions<T extends PermissionLike>(
  permissions: readonly T[],
): PermissionGroup<T>[] {
  const buckets = new Map<PermissionModule, T[]>()

  permissions.forEach((permission) => {
    const module = moduleOfPermission(permission.permCode)
    const bucket = buckets.get(module)
    if (bucket) bucket.push(permission)
    else buckets.set(module, [permission])
  })

  return MODULE_ORDER.filter((module) => buckets.has(module)).map((module) => ({
    module,
    permissions: [...(buckets.get(module) ?? [])].sort(comparePermission),
  }))
}

export interface GroupSelectionState {
  /** 组内权限总数 */
  total: number
  /** 组内已选数量 */
  selected: number
  /** 全选 */
  allSelected: boolean
  /** 半选：仅当 0 < 已选 < 总数，与 el-checkbox 的 indeterminate 语义一致 */
  indeterminate: boolean
}

/** 计算一个模块的全选 / 半选状态 */
export function groupSelectionState(
  selectedIds: readonly number[],
  permissions: readonly PermissionLike[],
): GroupSelectionState {
  const selectedSet = new Set(selectedIds)
  const total = permissions.length
  const selected = permissions.reduce(
    (count, permission) => count + (selectedSet.has(permission.id) ? 1 : 0),
    0,
  )

  return {
    total,
    selected,
    allSelected: total > 0 && selected === total,
    indeterminate: selected > 0 && selected < total,
  }
}

/**
 * 点击模块全选框：全选时取消本组，否则补齐本组。
 * 返回新的已选 ID 数组（保持升序），组外选择不受影响。
 */
export function toggleGroup(
  selectedIds: readonly number[],
  permissions: readonly PermissionLike[],
): number[] {
  const state = groupSelectionState(selectedIds, permissions)
  const next = new Set(selectedIds)

  permissions.forEach((permission) => {
    if (state.allSelected) next.delete(permission.id)
    else next.add(permission.id)
  })

  return [...next].sort((a, b) => a - b)
}

/**
 * 权限码 → 权限主键：GET 返回 code，PUT 需要 id。
 * 字典里不存在的 code 会被忽略（由调用方提示，不静默当成新权限）。
 */
export function permissionCodesToIds(
  codes: readonly string[],
  permissions: readonly PermissionLike[],
): number[] {
  const idByCode = new Map<string, number>()
  permissions.forEach((permission) => idByCode.set(permission.permCode, permission.id))

  const ids = new Set<number>()
  codes.forEach((code) => {
    const id = idByCode.get(code)
    if (id !== undefined) ids.add(id)
  })

  return [...ids].sort((a, b) => a - b)
}

/** 找出字典里不存在的权限码，用于加载角色授权后给出提示 */
export function unmatchedPermissionCodes(
  codes: readonly string[],
  permissions: readonly PermissionLike[],
): string[] {
  const knownCodes = new Set(permissions.map((permission) => permission.permCode))
  return codes.filter((code) => !knownCodes.has(code))
}

/** 两组权限 ID 是否等价（顺序无关），用于「未保存」判定 */
export function isSameSelection(
  left: readonly number[],
  right: readonly number[],
): boolean {
  if (left.length !== right.length) return false
  const sortedLeft = [...left].sort((a, b) => a - b)
  const sortedRight = [...right].sort((a, b) => a - b)
  return sortedLeft.every((value, index) => value === sortedRight[index])
}
