<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElTag, ElInput, ElButton, ElPagination, ElMessage } from 'element-plus'
import axios from '@/api/axios'
import type { IdentityMapAuditView } from '@/types'

const logs = ref<IdentityMapAuditView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const scriptIdFilter = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const params: Record<string, number> = {
      page: page.value - 1,
      size: size.value
    }
    const v = parseInt(scriptIdFilter.value, 10)
    if (Number.isInteger(v) && v > 0) params.scriptId = v
    const res = await axios.get('/desensitization/audits', { params }) as any
    logs.value = res.content
    total.value = res.totalElements
  } catch {
    ElMessage.error('加载脱敏审计记录失败')
  } finally {
    loading.value = false
  }
}

const tagType = (action: string): 'danger' | 'warning' | 'primary' => {
  if (action === 'VIEW_RAW') return 'danger'
  if (action === 'DECIDE_GROUP') return 'primary'
  return 'warning'
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-gray-800">双盲脱敏审计</h2>
      <p class="text-gray-500 mt-1">追溯哪位管理员在何时查看过原稿、代号映射，或核对了哪个身份分组</p>
    </div>

    <div class="bg-white rounded-xl shadow-sm p-6">
      <div class="flex items-center gap-3 mb-4">
        <ElInput
          v-model="scriptIdFilter"
          placeholder="按脚本编号过滤"
          style="width: 200px"
          clearable
          @keyup.enter="page = 1; load()"
        />
        <ElButton type="primary" @click="page = 1; load()">查询</ElButton>
      </div>

      <ElTable :data="logs" v-loading="loading" border stripe>
        <ElTableColumn prop="createdAt" label="时间" width="180" />
        <ElTableColumn label="动作" width="140">
          <template #default="{ row }">
            <ElTag size="small" :type="tagType(row.action)">{{ row.actionLabel }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="viewerName" label="操作管理员" width="140" />
        <ElTableColumn prop="ipAddress" label="IP" width="150" />
        <ElTableColumn prop="detail" label="详情" min-width="360" show-overflow-tooltip />
      </ElTable>

      <div class="flex justify-end mt-4">
        <ElPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>
