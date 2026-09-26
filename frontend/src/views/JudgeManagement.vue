<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElTable, ElTableColumn, ElTag, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { getJudges, createJudge, updateJudge, deleteJudge } from '@/api/judge'
import type { JudgeResponse, CreateJudgeRequest } from '@/types'

const judges = ref<JudgeResponse[]>([])
const showModal = ref(false)
const isEdit = ref(false)
const loading = ref(false)

const form = ref<CreateJudgeRequest>({
  name: '',
  role: 'JUDGE',
  expertise: ''
})

const currentId = ref<number | null>(null)

const loadData = async () => {
  loading.value = true
  try {
    const result = await getJudges()
    judges.value = result as JudgeResponse[]
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  isEdit.value = false
  currentId.value = null
  form.value = { name: '', role: 'JUDGE', expertise: '' }
  showModal.value = true
}

const handleEdit = (judge: JudgeResponse) => {
  isEdit.value = true
  currentId.value = judge.id
  form.value = { name: judge.name, role: judge.role, expertise: judge.expertise }
  showModal.value = true
}

const handleSubmit = async () => {
  if (!form.value.name) {
    ElMessage.warning('请输入评委姓名')
    return
  }
  
  try {
    if (isEdit.value && currentId.value) {
      await updateJudge(currentId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createJudge(form.value)
      ElMessage.success('创建成功')
    }
    showModal.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '操作失败')
  }
}

const handleDelete = async (judge: JudgeResponse) => {
  try {
    await deleteJudge(judge.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '删除失败')
  }
}

const getRoleLabel = (role: string) => {
  const map: Record<string, string> = { ADMIN: '管理员', JUDGE: '评委', VIEWER: '查看员' }
  return map[role] || role
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">评委管理</h2>
        <p class="text-gray-500 mt-1">管理评审人员信息</p>
      </div>
      <ElButton type="primary" @click="handleCreate">新增评委</ElButton>
    </div>
    
    <ElTable :data="judges" stripe v-loading="loading">
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="name" label="姓名" width="120" />
      <ElTableColumn prop="role" label="角色" width="100">
        <template #default="{ row }">
          <ElTag type="info">{{ getRoleLabel((row as JudgeResponse).role) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="expertise" label="专业领域" min-width="200" />
      <ElTableColumn prop="createdAt" label="创建时间" width="180" />
      <ElTableColumn label="操作" width="140">
        <template #default="{ row }">
          <ElButton size="small" @click="handleEdit(row as JudgeResponse)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="handleDelete(row as JudgeResponse)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    
    <ElDialog v-model="showModal" :title="isEdit ? '编辑评委' : '新增评委'">
      <ElForm :model="form" label-width="100px">
        <ElFormItem label="姓名" required>
          <ElInput v-model="form.name" placeholder="请输入姓名" />
        </ElFormItem>
        
        <ElFormItem label="专业领域">
          <ElInput v-model="form.expertise" placeholder="请输入专业领域" />
        </ElFormItem>
      </ElForm>
      
      <template #footer>
        <ElButton @click="showModal = false">取消</ElButton>
        <ElButton type="primary" @click="handleSubmit">{{ isEdit ? '保存' : '创建' }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>