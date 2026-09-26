<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElCard, ElForm, ElFormItem, ElInput, ElTag, ElMessage, ElDivider } from 'element-plus'
import { getCurrentUser, changePassword } from '@/api/user'
import type { UserResponse } from '@/types'

const user = ref<UserResponse | null>(null)
const loading = ref(false)

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const result = await getCurrentUser()
    user.value = result as UserResponse
  } catch (error) {
    ElMessage.error('加载用户信息失败')
  } finally {
    loading.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordForm.value.oldPassword) {
    ElMessage.warning('请输入旧密码')
    return
  }
  if (!passwordForm.value.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  
  try {
    await changePassword(passwordForm.value.oldPassword, passwordForm.value.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    window.location.href = '/login'
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '密码修改失败')
  }
}

const getRoleLabel = (role: string) => {
  const map: Record<string, string> = { ADMIN: '管理员', JUDGE: '评委', VIEWER: '查看员' }
  return map[role] || role
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = { ACTIVE: '正常', INACTIVE: '禁用' }
  return map[status] || status
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6 max-w-2xl mx-auto">
    <div>
      <h2 class="text-2xl font-bold text-gray-800">个人信息</h2>
      <p class="text-gray-500 mt-1">查看和管理您的个人信息</p>
    </div>
    
    <ElCard v-loading="loading">
      <div v-if="user" class="space-y-4">
        <div class="flex items-center gap-4">
          <div class="w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center text-2xl">
            {{ user.name.charAt(0) }}
          </div>
          <div>
            <h3 class="text-lg font-semibold">{{ user.name }}</h3>
            <ElTag type="info" class="mt-1">{{ getRoleLabel(user.role) }}</ElTag>
          </div>
        </div>
        
        <ElDivider />
        
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="text-sm text-gray-500">用户名</label>
            <p class="mt-1 text-gray-800">{{ user.username }}</p>
          </div>
          <div>
            <label class="text-sm text-gray-500">状态</label>
            <p class="mt-1">
              <ElTag :type="user.status === 'ACTIVE' ? 'success' : 'danger'">
                {{ getStatusLabel(user.status) }}
              </ElTag>
            </p>
          </div>
          <div>
            <label class="text-sm text-gray-500">邮箱</label>
            <p class="mt-1 text-gray-800">{{ user.email || '-' }}</p>
          </div>
          <div>
            <label class="text-sm text-gray-500">手机号</label>
            <p class="mt-1 text-gray-800">{{ user.phone || '-' }}</p>
          </div>
          <div class="col-span-2">
            <label class="text-sm text-gray-500">创建时间</label>
            <p class="mt-1 text-gray-800">{{ user.createdAt }}</p>
          </div>
        </div>
      </div>
    </ElCard>
    
    <ElCard title="修改密码">
      <ElForm :model="passwordForm" label-width="100px" class="space-y-4">
        <ElFormItem label="旧密码">
          <ElInput v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" />
        </ElFormItem>
        <ElFormItem label="新密码">
          <ElInput v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" />
        </ElFormItem>
        <ElFormItem label="确认密码">
          <ElInput v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="handleChangePassword">修改密码</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
  </div>
</template>