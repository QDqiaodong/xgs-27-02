<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElCard, ElTag, ElButton, ElMessage } from 'element-plus'
import { getLowScoreComments } from '@/api/statistics'
import type { LowScoreCommentResponse } from '@/types'

const comments = ref<LowScoreCommentResponse[]>([])
const loading = ref(false)

const loadComments = async () => {
  loading.value = true
  try {
    comments.value = await getLowScoreComments()
  } catch (error) {
    ElMessage.error('加载意见汇总失败')
  } finally {
    loading.value = false
  }
}

const getGradeClass = (grade: string) => {
  const map: Record<string, string> = {
    S: 'bg-red-500',
    A: 'bg-orange-500',
    B: 'bg-blue-500',
    C: 'bg-gray-500'
  }
  return map[grade] || ''
}

const exportComments = () => {
  if (comments.value.length === 0) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  
  let csv = '脚本ID,脚本标题,总分,等级,评委姓名,评审意见,提交时间\n'
  comments.value.forEach(item => {
    item.comments.forEach(comment => {
      csv += `${item.scriptId},${item.scriptTitle},${item.totalScore},${item.grade},${comment.judgeName},${comment.comment.replace(/,/g, '，')},${comment.createdAt}\n`
    })
  })
  
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '低分稿件评审意见汇总.csv'
  link.click()
  URL.revokeObjectURL(link.href)
  
  ElMessage.success('导出成功')
}

onMounted(() => {
  loadComments()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">意见汇总</h2>
        <p class="text-gray-500 mt-1">不合格低分脚本评审优化建议归档</p>
      </div>
      <ElButton type="primary" @click="exportComments">导出汇总</ElButton>
    </div>
    
    <ElCard shadow="never" v-if="comments.length > 0">
      <div class="space-y-6">
        <div
          v-for="item in comments"
          :key="item.scriptId"
          class="border rounded-xl p-6"
        >
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-4">
              <h3 class="text-lg font-semibold">{{ item.scriptTitle }}</h3>
              <ElTag :class="getGradeClass(item.grade)" text-color="white">
                {{ item.grade }}级
              </ElTag>
            </div>
            <div class="text-right">
              <span class="text-xl font-bold text-secondary">总分: {{ item.totalScore }}</span>
            </div>
          </div>
          
          <div class="space-y-3">
            <div
              v-for="(comment, index) in item.comments"
              :key="index"
              class="bg-gray-50 rounded-lg p-4"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="font-medium">{{ comment.judgeName }}</span>
                <span class="text-xs text-gray-400">{{ comment.createdAt }}</span>
              </div>
              <p class="text-gray-700">{{ comment.comment }}</p>
            </div>
          </div>
        </div>
      </div>
    </ElCard>
    
    <ElCard shadow="never" v-else>
      <div class="text-center text-gray-400 py-12">
        <div class="text-4xl mb-4">📋</div>
        <p>暂无低分稿件评审意见</p>
      </div>
    </ElCard>
  </div>
</template>