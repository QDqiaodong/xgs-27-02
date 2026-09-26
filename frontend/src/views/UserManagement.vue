<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElTable, ElTableColumn, ElTag, ElSelect, ElOption, ElMessage } from 'element-plus'
import { getUsers, createUser, updateUser, deleteUser } from '@/api/user'
import type { UserResponse } from '@/types'

const getRoleTagType = (role: string): 'danger' | 'warning' | 'info' | undefined => {
  const map: Record<string, 'danger' | 'warning' | 'info'> = { ADMIN: 'danger', JUDGE: 'warning', VIEWER: 'info' }
  return map[role]
}

const getRoleLabel = (role: string) => {
  const map: Record<string, string> = { ADMIN: '管理员', JUDGE: '评委', VIEWER: '查看员' }
  return map[role] || role
}

const users = ref<UserResponse[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  username: '',
  password: '',
  name: '',
  role: 'VIEWER',
  email: '',
  phone: ''
})

const loadUsers = async () => {
  try {
    users.value = await getUsers()
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  }
}

const handleCreate = () => {
  isEdit.value = false
  form.value = {
    username: '',
    password: '',
    name: '',
    role: 'VIEWER',
    email: '',
    phone: ''
  }
  dialogVisible.value = true
}

const handleEdit = (user: UserResponse) => {
  isEdit.value = true
  form.value = {
    username: user.username,
    password: '',
    name: user.name,
    role: user.role,
    email: user.email || '',
    phone: user.phone || ''
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!form.value.username || !form.value.name) {
    ElMessage.warning('请填写用户名和姓名')
    return
  }

  try {
    if (isEdit.value) {
      const user = users.value.find(u => u.username === form.value.username)
      if (user) {
        await updateUser(user.id, form.value)
        ElMessage.success('更新成功')
      }
    } else {
      if (!form.value.password) {
        ElMessage.warning('请设置密码')
        return
      }
      await createUser(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (user: UserResponse) => {
  try {
    await deleteUser(user.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">用户管理</h2>
        <p class="text-gray-500 mt-1">管理系统用户账号和权限</p>
      </div>
      <ElButton type="primary" @click="handleCreate">新增用户</ElButton>
    </div>

    <ElTable :data="users" stripe>
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="username" label="用户名" />
      <ElTableColumn prop="name" label="姓名" />
      <ElTableColumn prop="role" label="角色">
        <template #default="{ row }">
          <ElTag :type="getRoleTagType(row.role)">
            {{ getRoleLabel(row.role) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="email" label="邮箱" />
      <ElTableColumn prop="phone" label="手机号" />
      <ElTableColumn prop="status" label="状态">
        <template #default="{ row }">
          <ElTag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="180">
        <template #default="{ row }">
          <ElButton size="small" @click="handleEdit(row as UserResponse)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="handleDelete(row as UserResponse)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑用户' : '新增用户'" v-model="dialogVisible" width="500px">
      <ElForm :model="form" label-width="100px" class="space-y-4">
        <ElFormItem label="用户名">
          <ElInput v-model="form.username" :disabled="isEdit" />
        </ElFormItem>
        <ElFormItem label="密码" v-if="!isEdit">
          <ElInput v-model="form.password" type="password" placeholder="请设置密码" />
        </ElFormItem>
        <ElFormItem label="密码" v-else>
          <ElInput v-model="form.password" type="password" placeholder="不填则保持原密码" />
        </ElFormItem>
        <ElFormItem label="姓名">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem label="角色">
          <ElSelect v-model="form.role">
            <ElOption label="管理员" value="ADMIN" />
            <ElOption label="评委" value="JUDGE" />
            <ElOption label="查看员" value="VIEWER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="邮箱">
          <ElInput v-model="form.email" />
        </ElFormItem>
        <ElFormItem label="手机号">
          <ElInput v-model="form.phone" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>