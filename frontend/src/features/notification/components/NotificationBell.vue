<template>
  <!-- 铃铛入口：只在具备 notification:view 时渲染，避免无权限账号看到一个点了就 403 的按钮 -->
  <el-badge
    v-if="canView"
    class="notif-bell"
    :value="store.badgeText"
    :hidden="!store.hasUnread"
    :max="99"
    type="danger"
    data-test="notification-badge"
  >
    <el-button
      text
      circle
      class="notif-bell__button"
      :aria-label="store.hasUnread ? `通知，${store.unreadCount} 条未读` : '通知，无未读'"
      :title="store.hasUnread ? `${store.unreadCount} 条未读通知` : '通知'"
      @click="emit('open')"
    >
      <!-- 内联 SVG：项目没有引入 @element-plus/icons-vue，不为一个图标新增依赖 -->
      <svg class="notif-bell__icon" viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 3a5.5 5.5 0 0 0-5.5 5.5v3.2c0 .6-.2 1.2-.6 1.7l-1.1 1.4a1 1 0 0 0 .8 1.6h13.8a1 1 0 0 0 .8-1.6l-1.1-1.4a2.9 2.9 0 0 1-.6-1.7V8.5A5.5 5.5 0 0 0 12 3Z"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linejoin="round"
        />
        <path d="M9.5 19a2.5 2.5 0 0 0 5 0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      </svg>
    </el-button>
  </el-badge>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useNotificationStore } from '../../../app/stores/notification'
import { usePermission } from '../../../shared/utils/permission'
import { PERMISSIONS } from '../../../shared/constants/permissions'

const emit = defineEmits<{ open: [] }>()

const store = useNotificationStore()
const { hasPermission } = usePermission()
/** 权限码来自 shared/constants/permissions.ts（前端 B 不改这个文件，只引用） */
const canView = computed(() => hasPermission(PERMISSIONS.NOTIFICATION_VIEW))
</script>

<style scoped>
.notif-bell {
  display: inline-flex;
  align-items: center;
}

.notif-bell__button {
  color: var(--el-text-color-regular);
}

.notif-bell__icon {
  width: 18px;
  height: 18px;
}
</style>
