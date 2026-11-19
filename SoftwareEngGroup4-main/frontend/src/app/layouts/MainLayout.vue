<template>
  <el-container class="layout" style="height: 100vh">
    <el-aside width="220px">
      <el-menu router>
        <el-menu-item index="/dashboard">控制台</el-menu-item>
        <el-menu-item index="/study-plans">学习计划</el-menu-item>
        <el-menu-item index="/files">文件</el-menu-item>
        <el-menu-item index="/chat">AI 聊天</el-menu-item>
        <el-menu-item index="/admin">管理后台</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>考研 AI 导学系统</el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
</script>
