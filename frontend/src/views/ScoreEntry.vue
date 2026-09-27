<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElMessage, ElCard, ElTag } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getScriptById } from '@/api/script'
import { getJudges, getMyJudgeProfile } from '@/api/judge'
import { createScore, getScoresByScriptId } from '@/api/score'
import { getRecusalsByScript } from '@/api/recusal'
import { getAssignmentsByScriptId } from '@/api/assignment'
import type {
  ScriptResponse, JudgeResponse, ScoreResponse,
  RecusalApplicationResponse, ScriptAssignmentResponse
} from '@/types'

const route = useRoute()
const router = useRouter()
const script = ref<ScriptResponse | null>(null)
const judges = ref<JudgeResponse[]>([])
const existingScores = ref<ScoreResponse[]>([])
const recusals = ref<RecusalApplicationResponse[]>([])
const assignments = ref<ScriptAssignmentResponse[]>([])
const currentUser = ref<any>(JSON.parse(localStorage.getItem('user') || 'null'))
const isJudgeRole = computed(() => currentUser.value?.role === 'JUDGE')

const scriptId = Number(route.params.id)

const form = ref({
  judgeId: 0,
  creativityScore: 5,
  commercialScore: 5,
  difficultyScore: 5,
  logicScore: 5,
  audienceScore: 5,
  comment: ''
})

const dimensions = [
  { key: 'creativityScore', label: '创意', weight: '25%', desc: '脚本创意独特性、新颖程度' },
  { key: 'commercialScore', label: '商业潜力', weight: '20%', desc: '变现能力、市场价值' },
  { key: 'difficultyScore', label: '落地难度', weight: '15%', desc: '拍摄制作难度、可行性' },
  { key: 'logicScore', label: '逻辑完整度', weight: '20%', desc: '故事结构、叙事逻辑' },
  { key: 'audienceScore', label: '受众适配', weight: '20%', desc: '与目标受众匹配程度' }
]

const loadData = async () => {
  try {
    const [s, js, ss, rs, as] = await Promise.all([
      getScriptById(scriptId),
      getJudges(),
      getScoresByScriptId(scriptId),
      getRecusalsByScript(scriptId),
      getAssignmentsByScriptId(scriptId).catch(() => [])
    ])
    script.value = s
    judges.value = js
    existingScores.value = ss
    recusals.value = rs
    assignments.value = as as ScriptAssignmentResponse[]

    if (isJudgeRole.value) {
      const me = await getMyJudgeProfile().catch(() => null)
      if (me) form.value.judgeId = me.id
    }
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const validScores = computed(() => existingScores.value.filter(s => s.valid))

// 评委在此脚本上的回避状态
const openRecusalJudgeIds = computed(() => new Set(
  recusals.value
    .filter(r => r.status === 'PENDING' || r.status === 'SUPPLEMENT_REQUESTED' || r.status === 'APPROVED')
    .map(r => r.judgeId)
))

const isJudgeDisabled = (judgeId: number) => {
  if (validScores.value.some(s => s.judgeId === judgeId)) return true
  if (openRecusalJudgeIds.value.has(judgeId)) return true
  const assignment = assignments.value.find(a => a.judgeId === judgeId)
  if (assignment && assignment.status === 'RECUSED') return true
  return false
}

const disableReason = (judgeId: number): string => {
  if (validScores.value.some(s => s.judgeId === judgeId)) return '(已打分)'
  if (openRecusalJudgeIds.value.has(judgeId)) return '(回避处理中/已回避)'
  const assignment = assignments.value.find(a => a.judgeId === judgeId)
  if (assignment && assignment.status === 'RECUSED') return '(已回避)'
  return ''
}

const selectedJudgeBlocked = computed(() => {
  if (!form.value.judgeId) return ''
  const recusal = recusals.value.find(
    r => r.judgeId === form.value.judgeId &&
      (r.status === 'PENDING' || r.status === 'SUPPLEMENT_REQUESTED' || r.status === 'APPROVED')
  )
  if (recusal) {
    return recusal.status === 'APPROVED'
      ? '该评委已被批准回避，不能提交评分'
      : '该评委存在未处理的回避申请，处理完成前不能提交评分'
  }
  return ''
})

const handleSubmit = async () => {
  if (!form.value.judgeId) {
    ElMessage.warning('请选择评委')
    return
  }
  if (selectedJudgeBlocked.value) {
    ElMessage.warning(selectedJudgeBlocked.value)
    return
  }
  if (validScores.value.some(s => s.judgeId === form.value.judgeId)) {
    ElMessage.warning('该评委已对该脚本打分')
    return
  }

  try {
    await createScore({
      scriptId,
      judgeId: form.value.judgeId,
      creativityScore: form.value.creativityScore,
      commercialScore: form.value.commercialScore,
      difficultyScore: form.value.difficultyScore,
      logicScore: form.value.logicScore,
      audienceScore: form.value.audienceScore,
      comment: form.value.comment
    })
    ElMessage.success('打分成功')
    loadData()
    form.value = {
      judgeId: isJudgeRole.value ? form.value.judgeId : 0,
      creativityScore: 5,
      commercialScore: 5,
      difficultyScore: 5,
      logicScore: 5,
      audienceScore: 5,
      comment: ''
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '打分失败')
  }
}

const handleBack = () => {
  router.push(`/script/${scriptId}`)
}

onMounted(loadData)
</script>

<template>
  <div class="space-y-6 max-w-4xl mx-auto">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">评委打分</h2>
        <p class="text-gray-500 mt-1">为脚本进行多维度独立评分（有未处理回避申请的评委暂不能提交）</p>
      </div>
      <ElButton @click="handleBack">返回详情</ElButton>
    </div>

    <div v-if="script" class="bg-white rounded-xl shadow-sm p-6">
      <div class="mb-6 pb-6 border-b">
        <div class="flex items-center gap-2 mb-2">
          <h3 class="text-lg font-semibold">{{ script.title }}</h3>
          <ElTag size="small" type="info">双盲脱敏</ElTag>
          <ElTag v-if="script.pendingCount && script.pendingCount > 0" size="small" type="warning">
            {{ script.pendingCount }} 处身份线索待管理员核对
          </ElTag>
        </div>
        <div class="flex gap-4 mt-2 text-sm text-gray-500">
          <span>题材: {{ script.genre }}</span>
          <span>时长: {{ script.duration }}秒</span>
          <span>受众: {{ script.targetAudience }}</span>
        </div>
        <p class="mt-3 text-xs text-gray-400">
          您看到的标题与正文均为脱敏视图：投稿人的姓名、机构、电话、邮箱、社交账号已替换为稳定代号；
          与投稿身份无关的剧情人物姓名保留以保证脚本可读性。
        </p>
        <div v-if="script.description" class="mt-3 text-sm text-gray-700 whitespace-pre-wrap border-l-2 border-gray-200 pl-3">
          {{ script.description }}
        </div>
      </div>

      <ElForm :model="form" label-width="100px">
        <ElFormItem label="选择评委" required>
          <ElSelect v-model="form.judgeId" placeholder="请选择评委" :disabled="isJudgeRole">
            <ElOption
              v-for="judge in judges"
              :key="judge.id"
              :label="judge.name"
              :value="judge.id"
              :disabled="isJudgeDisabled(judge.id)"
            >
              <span>{{ judge.name }}</span>
              <span class="ml-2 text-xs text-gray-400">{{ disableReason(judge.id) }}</span>
            </ElOption>
          </ElSelect>
        </ElFormItem>

        <div v-if="selectedJudgeBlocked" class="mb-4">
          <ElTag type="warning">{{ selectedJudgeBlocked }}</ElTag>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-8">
          <div
            v-for="dim in dimensions"
            :key="dim.key"
            class="border rounded-xl p-4"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="font-medium">{{ dim.label }}</span>
              <span class="text-xs text-secondary bg-secondary/10 px-2 py-1 rounded">权重 {{ dim.weight }}</span>
            </div>
            <p class="text-xs text-gray-400 mb-4">{{ dim.desc }}</p>
            <div class="flex items-center gap-4">
              <input
                type="range"
                v-model.number="form[dim.key as keyof typeof form]"
                min="1"
                max="10"
                class="flex-1 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
              />
              <span class="text-xl font-bold text-primary w-8 text-center">{{ form[dim.key as keyof typeof form] }}</span>
            </div>
          </div>
        </div>

        <ElFormItem label="评审意见" class="mt-8">
          <ElInput v-model="form.comment" type="textarea" :rows="3" placeholder="请输入评审意见和优化建议" />
        </ElFormItem>

        <ElFormItem class="mt-8">
          <ElButton type="primary" @click="handleSubmit" size="large">提交评分</ElButton>
        </ElFormItem>
      </ElForm>
    </div>

    <ElCard title="已提交评分（含已失效评分审计记录）" shadow="never" class="mt-6">
      <div class="space-y-4">
        <div v-for="score in existingScores" :key="score.id"
          class="border rounded-lg p-4" :class="score.valid ? '' : 'opacity-60 bg-gray-50 border-dashed'">
          <div class="flex items-center justify-between mb-3">
            <span class="font-medium">
              {{ score.judgeName }}
              <ElTag v-if="!score.valid" size="small" type="info" class="ml-1">已失效（回避）</ElTag>
            </span>
            <span class="text-xs text-gray-400">{{ score.createdAt }}</span>
          </div>
          <div class="grid grid-cols-5 gap-2 text-sm">
            <div class="text-center">
              <div class="text-lg font-bold text-gray-800">{{ score.creativityScore }}</div>
              <div class="text-xs text-gray-400">创意</div>
            </div>
            <div class="text-center">
              <div class="text-lg font-bold text-gray-800">{{ score.commercialScore }}</div>
              <div class="text-xs text-gray-400">商业</div>
            </div>
            <div class="text-center">
              <div class="text-lg font-bold text-gray-800">{{ score.difficultyScore }}</div>
              <div class="text-xs text-gray-400">难度</div>
            </div>
            <div class="text-center">
              <div class="text-lg font-bold text-gray-800">{{ score.logicScore }}</div>
              <div class="text-xs text-gray-400">逻辑</div>
            </div>
            <div class="text-center">
              <div class="text-lg font-bold text-gray-800">{{ score.audienceScore }}</div>
              <div class="text-xs text-gray-400">受众</div>
            </div>
          </div>
          <div v-if="score.comment" class="mt-3 text-sm text-gray-500 bg-gray-50 p-2 rounded">
            {{ score.comment }}
          </div>
        </div>
      </div>
    </ElCard>
  </div>
</template>
