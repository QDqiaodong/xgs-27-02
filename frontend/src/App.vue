<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'

const router = useRouter()
const route = useRoute()

const isLoggedIn = ref(false)
const user = ref<any>(null)

const menuItems = computed(() => {
  const baseItems = [
    { path: '/dashboard', label: '数据概览' },
    { path: '/scripts', label: '脚本列表（脱敏）' },
    { path: '/statistics', label: '统计分析' },
    { path: '/comments', label: '意见汇总' }
  ]

  if (user.value && user.value.role === 'ADMIN') {
    baseItems.splice(2, 0, { path: '/script/create', label: '脚本录入' })
    baseItems.push(
      { path: '/assignments', label: '分配管理' },
      { path: '/recusals', label: '回避审批' },
      { path: '/judges', label: '评委管理' },
      { path: '/users', label: '用户管理' },
      { path: '/logs', label: '操作日志' },
      { path: '/desensitization/audits', label: '脱敏审计' }
    )
  } else if (user.value && user.value.role === 'JUDGE') {
    baseItems.push({ path: '/my-recusals', label: '我的回避申请' })
  }

  baseItems.push({ path: '/profile', label: '个人信息' })

  return baseItems
})

const activeMenu = computed(() => route.path)

const getRoleLabel = (role: string) => {
  const map: Record<string, string> = { ADMIN: '管理员', JUDGE: '评委', VIEWER: '查看员' }
  return map[role] || role
}

const checkLogin = () => {
  const token = localStorage.getItem('token')
  const userData = localStorage.getItem('user')
  if (token && userData) {
    isLoggedIn.value = true
    user.value = JSON.parse(userData)
  }
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  isLoggedIn.value = false
  user.value = null
  router.push('/login')
}

onMounted(() => {
  checkLogin()
})
</script>

<template>
  <div v-if="!isLoggedIn && route.path !== '/login'" class="min-h-screen bg-gradient-to-br from-blue-600 to-blue-800 flex items-center justify-center">
    <div class="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md text-center">
      <div class="text-4xl mb-4">🔒</div>
      <h1 class="text-xl font-bold text-gray-800 mb-4">请先登录</h1>
      <ElButton type="primary" @click="router.push('/login')">跳转登录</ElButton>
    </div>
  </div>

  <div v-else-if="route.path === '/login'" class="min-h-screen">
    <router-view />
  </div>

  <div v-else class="flex min-h-screen">
    <aside class="w-64 bg-primary text-white flex flex-col">
      <div class="p-6 border-b border-white/20">
        <h1 class="text-xl font-bold">短视频脚本评审系统</h1>
        <p class="text-sm text-gray-300 mt-1">多评委加权自动分级</p>
      </div>
      
      <nav class="flex-1 p-4">
        <ul class="space-y-2">
          <li v-for="item in menuItems" :key="item.path">
            <button
              @click="router.push(item.path)"
              :class="[
                'w-full text-left px-4 py-3 rounded-lg transition-all',
                activeMenu === item.path
                  ? 'bg-secondary text-white shadow-lg'
                  : 'hover:bg-white/10 text-gray-200'
              ]"
            >
              {{ item.label }}
            </button>
          </li>
        </ul>
      </nav>
      
      <div class="p-4 border-t border-white/20">
        <ElDropdown>
          <span class="flex items-center gap-2 text-sm">
            <span>{{ user?.name }}</span>
            <span class="text-xs bg-white/20 px-2 py-1 rounded">{{ getRoleLabel(user?.role || '') }}</span>
          </span>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem @click="router.push('/profile')">个人信息</ElDropdownItem>
              <ElDropdownItem @click="handleLogout">退出登录</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </aside>
    
    <main class="flex-1 p-6 overflow-auto">
      <router-view />
    </main>
  </div>
</template>