<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'

const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)

const handleLogin = async () => {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const data = await login(username.value, password.value)
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error('用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-blue-600 to-blue-800 flex items-center justify-center">
    <div class="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md">
      <div class="text-center mb-8">
        <div class="text-4xl mb-4">📝</div>
        <h1 class="text-2xl font-bold text-gray-800">短视频脚本评审系统</h1>
        <p class="text-gray-500 mt-2">多评委加权自动分级评审</p>
      </div>
      
      <ElForm :model="{ username, password }" label-width="0" class="space-y-4">
        <ElFormItem>
          <ElInput 
            v-model="username" 
            placeholder="用户名" 
            prefix-icon="User"
            size="large"
          />
        </ElFormItem>
        <ElFormItem>
          <ElInput 
            v-model="password" 
            type="password" 
            placeholder="密码" 
            prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton 
            type="primary" 
            size="large" 
            class="w-full" 
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </ElButton>
        </ElFormItem>
      </ElForm>
      
      <div class="text-center mt-6 text-gray-400 text-sm">
        <p>默认账号：admin / admin123</p>
        <p>评委账号：judge1 / admin123</p>
      </div>
    </div>
  </div>
</template>
