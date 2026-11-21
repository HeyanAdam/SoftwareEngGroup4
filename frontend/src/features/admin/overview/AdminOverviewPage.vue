<script setup lang="ts">
import AdminPageHeader from '../layout/components/AdminPageHeader.vue'
</script>

<template>
  <div class="admin-overview">
    <AdminPageHeader
      title="运营概览"
      description="数据大屏组接入统计接口后，本页由大屏组件替换，当前为占位内容。"
    />

    <div class="admin-overview__grid">
      <div class="admin-overview__card">
        <span class="admin-overview__label">注册用户</span>
        <span class="admin-overview__value">—</span>
        <span class="admin-overview__note">待接入统计接口</span>
      </div>
      <div class="admin-overview__card">
        <span class="admin-overview__label">今日活跃</span>
        <span class="admin-overview__value">—</span>
        <span class="admin-overview__note">待接入统计接口</span>
      </div>
      <div class="admin-overview__card">
        <span class="admin-overview__label">禁用账号</span>
        <span class="admin-overview__value">—</span>
        <span class="admin-overview__note">待接入统计接口</span>
      </div>
    </div>

    <div class="admin-overview__hint">
      <h3>本节可替换范围</h3>
      <p>
        图表与统计卡片由数据大屏组提供组件后整体替换；
        路由名 <code>AdminDashboard</code> 与路径 <code>/admin/dashboard</code> 保持不变。
      </p>
    </div>
  </div>
</template>

<style scoped>
.admin-overview__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.admin-overview__card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px 18px;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.admin-overview__label {
  font-size: 13px;
  color: #8a9a93;
}

.admin-overview__value {
  font-size: 30px;
  font-weight: 600;
  color: #1f4d3d;
}

.admin-overview__note {
  font-size: 12px;
  color: #b4bfba;
}

.admin-overview__hint {
  margin-top: 20px;
  padding: 18px 20px;
  background: #fff;
  border: 1px dashed var(--el-border-color-light);
  border-radius: 8px;
}

.admin-overview__hint h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #1f2b26;
}

.admin-overview__hint p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #7d8b85;
}
</style>
