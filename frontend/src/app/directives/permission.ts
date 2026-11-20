import type { Directive } from 'vue'
import { usePermission } from '../../shared/utils/permission'

/**
 * v-permission 指令：无权限时移除该元素
 * 用法：<el-button v-permission="'file:upload'">上传</el-button>
 */
const permissionDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const { hasPermission } = usePermission()
    if (!binding.value) return
    if (!hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permissionDirective
