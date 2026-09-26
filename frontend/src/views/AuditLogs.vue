<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElTag, ElButton, ElMessage } from 'element-plus'
import { getLogs } from '@/api/log'
import type { AuditLogResponse } from '@/types'

const logs = ref<AuditLogResponse[]>([])
const loading = ref(false)
const currentPage = ref(0)

const loadData = async (page = 0) => {
  loading.value = true
  try {
    const result = await getLogs({ page, size: 20 })
    logs.value = (result as any).content || []
    currentPage.value = page
  } catch (error) {
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

const getModuleLabel = (module: string) => {
  const map: Record<string, string> = {
    Script: '脚本管理',
    Score: '评分管理',
    Judge: '评委管理',
    User: '用户管理',
    Auth: '认证',
    Assignment: '分配管理',
    Recusal: '利益冲突回避',
    Statistics: '统计分析'
  }
  return map[module] || module
}

const getActionLabel = (action: string) => {
  const map: Record<string, string> = {
    createScript: '创建脚本',
    getScripts: '查询脚本',
    getScriptById: '查看脚本详情',
    updateScript: '更新脚本',
    deleteScript: '删除脚本',
    createScore: '提交评分',
    getScoresByScriptId: '查询评分',
    createJudge: '创建评委',
    updateJudge: '更新评委',
    deleteJudge: '删除评委',
    createUser: '创建用户',
    updateUser: '更新用户',
    deleteUser: '删除用户',
    login: '登录',
    assignScript: '分配脚本',
    getStatistics: '查询统计',
    create: '申报回避',
    supplement: '补充回避材料',
    decide: '处理回避申请',
    assignSubstitute: '指定替补评委',
    getSubstituteCandidates: '查询替补候选',
    listAll: '查询回避列表',
    listByScript: '按脚本查询回避',
    listMine: '查询本人回避',
    getDetail: '查看回避详情',
    getReviewLogs: '查询回避轨迹'
  }
  return map[action] || action
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-gray-800">操作日志</h2>
      <p class="text-gray-500 mt-1">查看系统操作记录</p>
    </div>
    
    <ElTable :data="logs" stripe v-loading="loading">
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="module" label="模块" width="120">
        <template #default="{ row }">
          <ElTag type="info">{{ getModuleLabel((row as AuditLogResponse).module) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="action" label="操作" width="140">
        <template #default="{ row }">
          {{ getActionLabel((row as AuditLogResponse).action) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="userName" label="操作人" width="100" />
      <ElTableColumn prop="ipAddress" label="IP地址" width="140" />
      <ElTableColumn prop="success" label="状态" width="80">
        <template #default="{ row }">
          <ElTag :type="(row as AuditLogResponse).success ? 'success' : 'danger'">
            {{ (row as AuditLogResponse).success ? '成功' : '失败' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="errorMessage" label="错误信息" min-width="200">
        <template #default="{ row }">
          <span v-if="(row as AuditLogResponse).errorMessage" class="text-red-500">{{ (row as AuditLogResponse).errorMessage }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="操作时间" width="180" />
    </ElTable>
    
    <div class="flex justify-center gap-4">
      <ElButton :disabled="currentPage === 0" @click="loadData(currentPage - 1)">上一页</ElButton>
      <span class="mt-1">第 {{ currentPage + 1 }} 页</span>
      <ElButton @click="loadData(currentPage + 1)">下一页</ElButton>
    </div>
  </div>
</template>