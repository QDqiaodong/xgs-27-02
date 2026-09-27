<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElCard, ElRow, ElCol, ElStatistic, ElTag } from 'element-plus'
import { useRouter } from 'vue-router'
import { getScripts } from '@/api/script'
import { getGradeDistribution } from '@/api/statistics'
import type { ScriptResponse, GradeDistributionResponse } from '@/types'

const router = useRouter()

const totalScripts = ref(0)
const pendingScripts = ref(0)
const reviewingScripts = ref(0)
const pendingSupplementScripts = ref(0)
const gradedScripts = ref(0)
const gradeDistribution = ref<GradeDistributionResponse[]>([])
const recentScripts = ref<ScriptResponse[]>([])

const loadData = async () => {
  try {
    const scripts = await getScripts({ page: 0, size: 200 })
    totalScripts.value = scripts.totalElements
    pendingScripts.value = scripts.content.filter((s: ScriptResponse) => s.status === 'PENDING').length
    reviewingScripts.value = scripts.content.filter((s: ScriptResponse) => s.status === 'REVIEWING').length
    pendingSupplementScripts.value =
      scripts.content.filter((s: ScriptResponse) => s.status === 'PENDING_SUPPLEMENT').length
    gradedScripts.value = scripts.content.filter((s: ScriptResponse) => s.status === 'GRADED').length
    recentScripts.value = scripts.content.slice(0, 5)

    const distribution = await getGradeDistribution()
    gradeDistribution.value = distribution
  } catch (error) {
    console.error('加载数据失败', error)
  }
}

const getGradeTagType = (grade: string): 'danger' | 'warning' | 'info' | 'success' | undefined => {
  const map: Record<string, 'danger' | 'warning' | 'info' | 'success'> = { S: 'danger', A: 'warning', B: 'info', C: 'success' }
  return map[grade]
}

const getStatusTagType = (status: string): 'danger' | 'warning' | 'info' | 'success' | undefined => {
  const map: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
    PENDING: 'info', REVIEWING: 'warning', PENDING_SUPPLEMENT: 'danger', GRADED: 'success'
  }
  return map[status]
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待分配', REVIEWING: '评审中', PENDING_SUPPLEMENT: '待补评', GRADED: '已分级'
  }
  return map[status] || status
}

const getGradeClass = (grade: string) => {
  const map: Record<string, string> = { S: 'bg-red-500', A: 'bg-orange-500', B: 'bg-blue-500', C: 'bg-gray-500' }
  return map[grade] || ''
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-gray-800">数据概览</h2>
      <p class="text-gray-500 mt-1">实时查看评审系统运行状态</p>
    </div>

    <ElRow :gutter="20">
      <ElCol :xs="12" :sm="8" :md="4" style="margin-bottom:12px">
        <ElCard>
          <ElStatistic title="脚本总数" :value="totalScripts" suffix="个" />
        </ElCard>
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4" style="margin-bottom:12px">
        <ElCard>
          <ElStatistic title="待分配" :value="pendingScripts" suffix="个" />
        </ElCard>
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4" style="margin-bottom:12px">
        <ElCard>
          <ElStatistic title="评审中" :value="reviewingScripts" suffix="个" />
        </ElCard>
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4" style="margin-bottom:12px">
        <ElCard>
          <ElStatistic title="待补评" :value="pendingSupplementScripts" suffix="个" />
        </ElCard>
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4" style="margin-bottom:12px">
        <ElCard>
          <ElStatistic title="已分级" :value="gradedScripts" suffix="个" />
        </ElCard>
      </ElCol>
    </ElRow>

    <ElRow :gutter="20">
      <ElCol :span="12">
        <ElCard title="等级分布" shadow="never">
          <div class="space-y-3">
            <div v-for="item in gradeDistribution" :key="item.grade" class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <ElTag :type="getGradeTagType(item.grade)">
                  {{ item.grade }}级
                </ElTag>
                <span class="text-gray-600">{{ item.count }}个</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-32 bg-gray-200 rounded-full h-2">
                  <div 
                    class="h-2 rounded-full transition-all duration-500"
                    :class="getGradeClass(item.grade)"
                    :style="{ width: `${item.percentage}%` }"
                  ></div>
                </div>
                <span class="text-gray-500 text-sm">{{ item.percentage.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
        </ElCard>
      </ElCol>
      <ElCol :span="12">
        <ElCard title="最新脚本（标题已脱敏）" shadow="never">
          <div class="space-y-3">
            <div 
              v-for="script in recentScripts" 
              :key="script.id" 
              class="flex items-center justify-between p-3 bg-gray-50 rounded-lg cursor-pointer hover:bg-gray-100"
              @click="router.push(`/script/${script.id}`)"
            >
              <div>
                <div class="font-medium text-gray-800">{{ script.title }}</div>
                <div class="text-sm text-gray-500">{{ script.genre }} · {{ script.duration }}秒</div>
              </div>
              <ElTag :type="getStatusTagType(script.status)">
                {{ getStatusLabel(script.status) }}
              </ElTag>
            </div>
          </div>
        </ElCard>
      </ElCol>
    </ElRow>
  </div>
</template>