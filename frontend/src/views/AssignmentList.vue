<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElTable, ElTableColumn, ElTag, ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElDatePicker, ElMessage } from 'element-plus'
import { getAssignments, assignScript } from '@/api/assignment'
import { getScripts } from '@/api/script'
import { getJudges } from '@/api/judge'
import type { ScriptAssignmentResponse, ScriptResponse, JudgeResponse, AssignScriptRequest } from '@/types'

const assignments = ref<ScriptAssignmentResponse[]>([])
const scripts = ref<ScriptResponse[]>([])
const judges = ref<JudgeResponse[]>([])
const showModal = ref(false)
const loading = ref(false)

const form = ref<AssignScriptRequest>({
  scriptId: 0,
  judgeIds: [],
  deadline: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const assignResult = await getAssignments()
    assignments.value = assignResult as ScriptAssignmentResponse[]
    
    const scriptPage = await getScripts({ page: 0, size: 100 })
    scripts.value = (scriptPage as any).content || []
    
    const judgeResult = await getJudges()
    judges.value = judgeResult as JudgeResponse[]
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleAssign = async () => {
  if (!form.value.scriptId) {
    ElMessage.warning('请选择脚本')
    return
  }
  if (form.value.judgeIds.length === 0) {
    ElMessage.warning('请至少选择一位评委')
    return
  }
  
  try {
    await assignScript(form.value)
    ElMessage.success('分配成功')
    showModal.value = false
    loadData()
    form.value = { scriptId: 0, judgeIds: [], deadline: '' }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '分配失败')
  }
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = { ASSIGNED: '已分配', COMPLETED: '已完成', RECUSED: '已回避' }
  return map[status] || status
}

const getStatusTagType = (status: string) => {
  const map: Record<string, 'warning' | 'success' | 'info'> = {
    ASSIGNED: 'warning', COMPLETED: 'success', RECUSED: 'info'
  }
  return map[status] || 'info'
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">脚本分配管理</h2>
        <p class="text-gray-500 mt-1">管理脚本与评委的分配关系</p>
      </div>
      <ElButton type="primary" @click="showModal = true">分配脚本</ElButton>
    </div>
    
    <ElTable :data="assignments" stripe v-loading="loading">
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="scriptTitle" label="脚本标题" min-width="200" />
      <ElTableColumn prop="judgeName" label="评委" width="120" />
      <ElTableColumn prop="assignedByName" label="分配人" width="120" />
      <ElTableColumn prop="status" label="状态" width="100">
        <template #default="{ row }">
          <ElTag :type="getStatusTagType(row.status)">{{ getStatusLabel(row.status) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="deadline" label="截止时间" width="180" />
      <ElTableColumn prop="assignedAt" label="分配时间" width="180" />
      <ElTableColumn label="回避/替补" width="140">
        <template #default="{ row }">
          <ElTag v-if="row.replacesAssignmentId" type="success" size="small">替补接管</ElTag>
          <ElTag v-else-if="row.status === 'RECUSED'" type="info" size="small">回避终止</ElTag>
          <span v-else class="text-gray-400">—</span>
        </template>
      </ElTableColumn>
    </ElTable>
    
    <ElDialog v-model="showModal" title="分配脚本" @close="form = { scriptId: 0, judgeIds: [], deadline: '' }">
      <ElForm :model="form" label-width="100px">
        <ElFormItem label="选择脚本" required>
          <ElSelect v-model="form.scriptId" placeholder="请选择脚本">
            <ElOption
              v-for="script in scripts.filter((s: ScriptResponse) => s.status !== 'GRADED')"
              :key="script.id"
              :label="script.title"
              :value="script.id"
            />
          </ElSelect>
        </ElFormItem>
        
        <ElFormItem label="选择评委" required>
          <ElSelect v-model="form.judgeIds" multiple placeholder="请选择评委">
            <ElOption
              v-for="judge in judges"
              :key="judge.id"
              :label="judge.name"
              :value="judge.id"
            />
          </ElSelect>
        </ElFormItem>
        
        <ElFormItem label="截止时间">
          <ElDatePicker
            v-model="form.deadline"
            type="datetime"
            placeholder="选择截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
      </ElForm>
      
      <template #footer>
        <ElButton @click="showModal = false">取消</ElButton>
        <ElButton type="primary" @click="handleAssign">确认分配</ElButton>
      </template>
    </ElDialog>
  </div>
</template>