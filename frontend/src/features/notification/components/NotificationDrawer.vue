<template>
  <el-drawer
    v-model="visible"
    title="通知中心"
    direction="rtl"
    :size="drawerSize"
    class="notif-drawer"
    @open="handleOpen"
  >
    <template #header>
      <div class="notif-drawer__header">
        <span class="notif-drawer__title">通知中心</span>
        <span v-if="store.hasUnread" class="notif-drawer__count">{{ store.unreadCount }} 条未读</span>
      </div>
    </template>

    <div class="notif-drawer__toolbar">
      <el-radio-group v-model="status" size="small" @change="handleStatusChange">
        <el-radio-button label="UNREAD">未读</el-radio-button>
        <el-radio-button label="ALL">全部</el-radio-button>
      </el-radio-group>
      <el-button
        v-if="canUpdate"
        link
        type="primary"
        size="small"
        :loading="store.markingAll"
        :disabled="!store.hasUnread"
        data-test="mark-all-read"
        @click="handleMarkAll"
      >
        全部已读
      </el-button>
    </div>

    <el-alert
      v-if="store.errorMessage"
      class="notif-drawer__error"
      type="error"
      :title="store.errorMessage"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button link type="primary" size="small" @click="reload">重试</el-button>
      </template>
    </el-alert>

    <div v-loading="store.loadingList && store.notifications.length === 0" class="notif-drawer__body">
      <div v-if="!store.loadingList && store.notifications.length === 0" class="notif-drawer__empty">
        <p class="notif-drawer__empty-title">{{ status === 'UNREAD' ? '暂时没有未读通知' : '暂时没有通知' }}</p>
        <p class="notif-drawer__empty-desc">学习计划到期、逾期和每日计划生成时会在这里提醒你。</p>
      </div>

      <ul v-else class="notif-drawer__list">
        <li
          v-for="item in store.notifications"
          :key="item.id"
          :class="notificationItemClass(item.read)"
          data-test="notification-item"
          @click="handleItemClick(item)"
        >
          <div class="notif-item__top">
            <span class="notif-item__dot" :class="{ 'notif-item__dot--active': !item.read }" aria-hidden="true" />
            <el-tag :type="severityTagType(item.severity)" size="small" effect="light">
              {{ NOTIFICATION_TYPE_LABELS[item.type] }}
            </el-tag>
            <span class="notif-item__title">{{ item.title }}</span>
            <span class="notif-item__time">{{ formatNotificationTime(item.createdAt) }}</span>
          </div>
          <p class="notif-item__content">{{ item.content }}</p>
          <div v-if="isSafeActionPath(item.actionPath)" class="notif-item__actions">
            <el-button link type="primary" size="small" data-test="notification-action" @click.stop="goAction(item)">
              去处理
            </el-button>
          </div>
        </li>
      </ul>

      <div v-if="store.hasMore" class="notif-drawer__more">
        <el-button size="small" :loading="store.loadingList" @click="loadMore">加载更多</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '../../../app/stores/notification'
import { usePermission } from '../../../shared/utils/permission'
import { PERMISSIONS } from '../../../shared/constants/permissions'
import type { UserNotification } from '../types'
import { NOTIFICATION_TYPE_LABELS, severityTagType } from '../types'
import { isSafeActionPath } from '../realtime/notificationRealtime'
import { formatNotificationTime, notificationItemClass } from '../utils'

/** 抽屉显隐由父组件 v-model 控制，切换时不需要重建组件 */
const visible = defineModel<boolean>({ default: false })

const router = useRouter()
const store = useNotificationStore()
const { hasPermission } = usePermission()
const canUpdate = computed(() => hasPermission(PERMISSIONS.NOTIFICATION_UPDATE))

/** 默认只看未读（契约 5.8 默认 UNREAD），要立刻降低干扰 */
const status = ref<'UNREAD' | 'ALL'>('UNREAD')

/** 手机上抽屉几乎占满屏，桌面上固定 420px；打开时按窗口宽度定，375 宽也不会被挤爆 */
const drawerSize = ref('420px')

/** 打开才拉列表：进入主布局时只拉一次未读数 */
function handleOpen(): void {
  drawerSize.value = window.innerWidth < 768 ? '92%' : '420px'
  status.value = 'UNREAD'
  void store.loadNotifications({ reset: true, status: 'UNREAD' })
}

function reload(): void {
  void store.loadNotifications({ reset: true, status: status.value })
}

function loadMore(): void {
  void store.loadNotifications({ status: status.value })
}

function handleStatusChange(): void {
  void store.loadNotifications({ reset: true, status: status.value })
}

async function handleItemClick(item: UserNotification): Promise<void> {
  if (item.read) {
    if (isSafeActionPath(item.actionPath)) goAction(item)
    return
  }
  const ok = await store.markRead(item.id)
  if (!ok) return
  // 未读筛选下读掉一条后保留在列表里（不闪走），用户点"全部/未读"再刷新时自然消失
  if (status.value === 'ALL' && isSafeActionPath(item.actionPath)) goAction(item)
}

/** 跳转只走站内路径，javascript:/外站/ //host 一律丢弃（契约 6.3 / 6.6） */
function goAction(item: UserNotification): void {
  if (!isSafeActionPath(item.actionPath)) return
  visible.value = false
  void router.push(item.actionPath)
}

async function handleMarkAll(): Promise<void> {
  const ok = await store.markAllRead()
  if (ok) ElMessage.success('已把通知全部标记为已读')
  else ElMessage.error(store.errorMessage || '全部已读失败')
  if (ok && status.value === 'UNREAD') await store.loadNotifications({ reset: true, status: 'UNREAD' })
}

/** 关闭抽屉时若还有正在进行的请求不影响 Store 状态，这里只做显隐同步 */
watch(visible, (next) => {
  if (!next) store.errorMessage = ''
})

onBeforeUnmount(() => {
  store.errorMessage = ''
})
</script>

<style scoped>
.notif-drawer__header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.notif-drawer__title {
  font-size: 16px;
  font-weight: 600;
}

.notif-drawer__count {
  font-size: 12px;
  color: var(--el-color-danger);
}

.notif-drawer__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.notif-drawer__error {
  margin-bottom: 12px;
}

.notif-drawer__body {
  min-height: 200px;
}

.notif-drawer__empty {
  padding: 40px 12px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.notif-drawer__empty-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.notif-drawer__empty-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}

.notif-drawer__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notif-item {
  padding: 10px 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.15s ease;
}

.notif-item:hover {
  background: var(--el-fill-color-light);
}

.notif-item--read {
  cursor: default;
}

.notif-item--read .notif-item__title,
.notif-item--read .notif-item__content {
  color: var(--el-text-color-secondary);
}

.notif-item__top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notif-item__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: transparent;
  flex: none;
}

.notif-item__dot--active {
  background: var(--el-color-danger);
}

.notif-item__title {
  flex: 1 1 160px;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notif-item__time {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.notif-item__content {
  margin: 6px 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.notif-item__actions {
  margin: 4px 0 0 14px;
}

.notif-drawer__more {
  padding: 12px 0;
  text-align: center;
}
</style>
