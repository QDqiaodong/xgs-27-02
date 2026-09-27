<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElButton, ElCard, ElTag, ElMessage, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption,
  ElAlert, ElEmpty
} from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getScriptById, getRawScript, getIdentityMap, decideIdentityGroup } from '@/api/script'
import { getScoresByScriptId, calculateGrade } from '@/api/score'
import { getRecusalsByScript, createRecusal } from '@/api/recusal'
import { getMyJudgeProfile, getJudges } from '@/api/judge'
import { getAssignmentsByScriptId } from '@/api/assignment'
import type {
  ScriptResponse, ScriptRawResponse, IdentityMapResponse, ScoreResponse, RecusalApplicationResponse,
  JudgeResponse, ScriptAssignmentResponse, RecusalType
} from '@/types'

const route = useRoute()
const router = useRouter()
const script = ref<ScriptResponse | null>(null)
const scores = ref<ScoreResponse[]>([])
const recusals = ref<RecusalApplicationResponse[]>([])
const assignments = ref<ScriptAssignmentResponse[]>([])
const loading = ref(false)
const currentUser = ref<any>(JSON.parse(localStorage.getItem('user') || 'null'))
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

const scriptId = Number(route.params.id)

const validScores = computed(() => scores.value.filter(s => s.valid))
const pendingCount = computed(() => script.value?.pendingCount ?? 0)

const loadScript = async () => {
  loading.value = true
  try {
    const [s, sc, rc, as] = await Promise.all([
      getScriptById(scriptId),
      getScoresByScriptId(scriptId),
      getRecusalsByScript(scriptId),
      getAssignmentsByScriptId(scriptId).catch(() => [])
    ])
    script.value = s
    scores.value = sc
    recusals.value = rc
    assignments.value = as as ScriptAssignmentResponse[]
  } catch (error) {
    ElMessage.error('加载脚本详情失败')
  } finally {
    loading.value = false
  }
}

const handleScore = () => {
  router.push(`/script/${scriptId}/score`)
}

const handleGrade = async () => {
  if (validScores.value.length < 2) {
    ElMessage.warning('至少需要2位评委的有效评分才能进行分级')
    return
  }
  try {
    await calculateGrade(scriptId)
    ElMessage.success('分级计算成功（仅使用当前有效评分）')
    loadScript()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '分级计算失败')
  }
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待评审',
    REVIEWING: '评审中',
    PENDING_SUPPLEMENT: '待补评',
    GRADED: '已分级'
  }
  return map[status] || status
}

const getGradeClass = (grade: string | null) => {
  if (!grade) return ''
  const map: Record<string, string> = {
    S: 'bg-red-500',
    A: 'bg-orange-500',
    B: 'bg-blue-500',
    C: 'bg-gray-500'
  }
  return map[grade] || ''
}

const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'bg-yellow-500',
    REVIEWING: 'bg-blue-500',
    PENDING_SUPPLEMENT: 'bg-purple-500',
    GRADED: 'bg-green-500'
  }
  return map[status] || ''
}

const recusalStatusMap: Record<string, { label: string; type: 'warning' | 'primary' | 'success' | 'danger' }> = {
  PENDING: { label: '回避申请待处理', type: 'warning' },
  SUPPLEMENT_REQUESTED: { label: '回避申请待补充', type: 'primary' },
  APPROVED: { label: '已有评委批准回避', type: 'success' },
  REJECTED: { label: '回避申请被驳回', type: 'danger' }
}

// ===================== 打印（仅脱敏内容可进入打印结果） =====================
const handlePrint = () => {
  window.print()
}

// ===================== 管理员：受控查看原稿（查看即审计） =====================
const rawDialog = ref(false)
const rawLoading = ref(false)
const rawScript = ref<ScriptRawResponse | null>(null)

const openRaw = async () => {
  rawDialog.value = true
  rawLoading.value = true
  rawScript.value = null
  try {
    rawScript.value = await getRawScript(scriptId)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '查看原稿被拒绝')
    rawDialog.value = false
  } finally {
    rawLoading.value = false
  }
}

// ===================== 管理员：代号映射核对（查看即审计） =====================
const mapDialog = ref(false)
const mapLoading = ref(false)
const identityMap = ref<IdentityMapResponse | null>(null)
const decidingGroupId = ref<number | null>(null)

const openMap = async () => {
  mapDialog.value = true
  mapLoading.value = true
  identityMap.value = null
  try {
    identityMap.value = await getIdentityMap(scriptId)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '查看代号映射被拒绝')
    mapDialog.value = false
  } finally {
    mapLoading.value = false
  }
}

const decide = async (groupId: number, decision: 'CONFIRMED_IDENTITY' | 'CONFIRMED_CHARACTER') => {
  decidingGroupId.value = groupId
  try {
    identityMap.value = await decideIdentityGroup(scriptId, groupId, decision)
    ElMessage.success(
      decision === 'CONFIRMED_IDENTITY'
        ? '已确认为投稿身份：评委视图使用稳定代号替换'
        : '已确认为剧情人物：评委视图保留原文'
    )
    // 核对只调整脱敏视图，重新加载脚本详情让评委视图结果同步
    await loadScript()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '核对失败')
  } finally {
    decidingGroupId.value = null
  }
}

// ===================== 申报回避 =====================
const recusalDialog = ref(false)
const myJudge = ref<JudgeResponse | null>(null)
const judges = ref<JudgeResponse[]>([])
const recusalForm = ref<{ judgeId: number; type: RecusalType; reason: string; evidence: string }>({
  judgeId: 0, type: 'AUTHOR', reason: '', evidence: ''
})

const openRecusal = async () => {
  recusalForm.value = { judgeId: 0, type: 'AUTHOR', reason: '', evidence: '' }
  recusalDialog.value = true
  try {
    if (currentUser.value?.role === 'JUDGE') {
      myJudge.value = await getMyJudgeProfile()
      if (myJudge.value) recusalForm.value.judgeId = myJudge.value.id
    } else if (currentUser.value?.role === 'ADMIN') {
      judges.value = (await getJudges()).filter(j => j.status === 'ACTIVE')
    }
  } catch {
    ElMessage.error('加载评委信息失败')
  }
}

const submitRecusal = async () => {
  const f = recusalForm.value
  if (!f.judgeId) {
    ElMessage.warning('请选择申报回避的评委')
    return
  }
  if (!f.reason.trim() || !f.evidence.trim()) {
    ElMessage.warning('回避说明与可核验依据均为必填')
    return
  }
  try {
    await createRecusal({
      scriptId,
      judgeId: f.judgeId,
      type: f.type,
      reason: f.reason.trim(),
      evidence: f.evidence.trim()
    })
    ElMessage.success('回避申请已提交，处理期间该评委不能提交评分')
    recusalDialog.value = false
    loadScript()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '提交回避申请失败')
  }
}

const typeLabelMap: Record<string, string> = {
  AUTHOR: '与作者存在利益关系',
  ORGANIZATION: '与来源机构存在利益关系',
  TOPIC: '与题材存在利益关系',
  OTHER: '其他利益关系'
}

onMounted(loadScript)
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between print:hidden">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">脚本详情</h2>
        <p class="text-gray-500 mt-1">当前展示为双盲脱敏视图，评委与查看员无法获得投稿人身份信息</p>
      </div>
      <div class="flex gap-2">
        <ElButton type="info" plain @click="handlePrint">打印脱敏视图</ElButton>
        <ElButton v-if="isAdmin" type="danger" plain @click="openRaw">受控查看原稿</ElButton>
        <ElButton v-if="isAdmin" type="warning" plain @click="openMap">代号映射核对</ElButton>
        <ElButton type="warning" @click="openRecusal"
          v-if="currentUser?.role === 'JUDGE' || currentUser?.role === 'ADMIN'">
          申报回避
        </ElButton>
        <ElButton type="primary" @click="handleScore">评委打分</ElButton>
        <ElButton type="success" @click="handleGrade" v-if="script?.status !== 'GRADED'">
          自动分级 ({{ validScores.length }}/2 有效)
        </ElButton>
        <ElButton @click="router.push('/')">返回列表</ElButton>
      </div>
    </div>

    <!-- 打印页眉：明确打印结果仍是脱敏视图 -->
    <div class="hidden print:block mb-4">
      <h1 class="text-xl font-bold">脚本双盲评审打印件（脱敏）</h1>
      <p class="text-xs text-gray-500">编号 #{{ script?.id }} · 本打印件不含任何投稿人原始身份信息</p>
    </div>

    <!-- 待核对提示：评委侧看到的是更保守的隐藏结果 -->
    <ElAlert v-if="pendingCount > 0"
      :title="isAdmin
        ? `有 ${pendingCount} 处可疑身份线索待管理员核对（评委侧已先按保守结果隐藏），请前往“代号映射核对”确认`
        : `该脚本有 ${pendingCount} 处身份线索正在管理员核对中，相关片段已暂时隐藏，不影响您就脚本内容本身评审`"
      :type="isAdmin ? 'warning' : 'info'"
      :closable="false"
      show-icon
      class="print:hidden"
    />

    <!-- 待补评提示 -->
    <div v-if="script?.status === 'PENDING_SUPPLEMENT'"
      class="bg-purple-50 border border-purple-200 text-purple-700 rounded-lg px-4 py-3 text-sm">
      该脚本因评委回避进入待补评状态：旧等级已撤销，待替补评委评分、有效评分重新达到 2 份后方可再次分级。
    </div>

    <div v-if="script" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2">
        <ElCard title="脚本信息（脱敏视图）" shadow="never">
          <div class="space-y-4">
            <div class="flex">
              <span class="w-24 text-gray-500">编号</span>
              <span class="font-medium">#{{ script.id }}</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">标题</span>
              <span class="font-medium">{{ script.title }}</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">题材</span>
              <span>{{ script.genre }}</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">时长</span>
              <span>{{ script.duration }}秒</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">目标受众</span>
              <span>{{ script.targetAudience }}</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">投稿身份</span>
              <span class="text-gray-400">双盲隐藏中（详见正文中的代号）</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">状态</span>
              <ElTag :class="getStatusClass(script.status)" text-color="white">
                {{ getStatusLabel(script.status) }}
              </ElTag>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">等级</span>
              <ElTag v-if="script.grade" :class="getGradeClass(script.grade)" text-color="white">
                {{ script.grade }}级
              </ElTag>
              <span v-else class="text-gray-400">未分级（回避撤销后需重新分级）</span>
            </div>
            <div class="flex">
              <span class="w-24 text-gray-500">总分</span>
              <span class="text-xl font-bold text-primary">{{ script.totalScore ?? '—' }}</span>
            </div>
            <div class="flex flex-col">
              <span class="w-24 text-gray-500">正文</span>
              <p class="mt-1 whitespace-pre-wrap">{{ script.description || '无' }}</p>
            </div>
            <div class="flex flex-col" v-if="script.attachmentNote">
              <span class="w-24 text-gray-500">附件说明</span>
              <p class="mt-1 whitespace-pre-wrap">{{ script.attachmentNote }}</p>
            </div>
          </div>
        </ElCard>
      </div>

      <div class="lg:col-span-1 space-y-6">
        <ElCard title="评分记录" shadow="never">
          <div class="text-xs text-gray-400 mb-3">
            有效评分 {{ validScores.length }} 份，共 {{ scores.length }} 条记录；失效评分保留原文供审计但不参与计算
          </div>
          <div v-if="scores.length === 0" class="text-center text-gray-400 py-8">
            暂无评分记录
          </div>
          <div v-else class="space-y-4">
            <div v-for="score in scores" :key="score.id"
              class="border rounded-lg p-3"
              :class="score.valid ? '' : 'opacity-60 bg-gray-50 border-dashed'">
              <div class="flex items-center justify-between mb-2">
                <span class="font-medium">
                  {{ score.judgeName }}
                  <ElTag v-if="!score.valid" size="small" type="info" class="ml-1">已失效（回避）</ElTag>
                  <ElTag v-else-if="score.isExtreme" size="small" type="warning" class="ml-1">极端分</ElTag>
                </span>
                <span class="text-xs text-gray-400">{{ score.createdAt }}</span>
              </div>
              <div class="grid grid-cols-2 gap-2 text-sm" :class="score.valid ? '' : 'line-through'">
                <div>创意: {{ score.creativityScore }}</div>
                <div>商业: {{ score.commercialScore }}</div>
                <div>难度: {{ score.difficultyScore }}</div>
                <div>逻辑: {{ score.logicScore }}</div>
                <div>受众: {{ score.audienceScore }}</div>
              </div>
              <div v-if="score.comment" class="mt-2 text-xs text-gray-500">
                {{ score.comment }}
              </div>
              <div v-if="!score.valid && score.invalidatedReason" class="mt-1 text-xs text-gray-400">
                {{ score.invalidatedReason }}
              </div>
            </div>
          </div>
        </ElCard>

        <ElCard title="回避状态" shadow="never">
          <div v-if="recusals.length === 0" class="text-center text-gray-400 py-4 text-sm">
            暂无回避记录
          </div>
          <div v-else class="space-y-3">
            <div v-for="r in recusals" :key="r.id" class="border rounded-lg p-3 text-sm">
              <div class="flex items-center justify-between">
                <span class="font-medium">{{ r.judgeName }} · {{ typeLabelMap[r.type] }}</span>
                <ElTag size="small" :type="recusalStatusMap[r.status].type">
                  {{ recusalStatusMap[r.status].label }}
                </ElTag>
              </div>
              <!-- 敏感说明仅本人/管理员可见 -->
              <div v-if="r.canViewSensitive && r.reason" class="mt-2 text-xs text-gray-500">
                <div class="font-medium text-gray-600">回避说明：</div>
                <p class="whitespace-pre-wrap">{{ r.reason }}</p>
              </div>
              <div v-else class="mt-2 text-xs text-gray-400">
                您只能查看回避状态，敏感说明不予展示
              </div>
              <div class="mt-2 text-xs text-gray-400">
                提交：{{ r.submittedByName }}<span v-if="r.submittedOnBehalf">（代提交）</span>
                <span v-if="r.decidedByName"> · 处理：{{ r.decidedByName }}</span>
              </div>
              <div v-if="r.substituteJudgeName" class="mt-1 text-xs text-green-600">
                替补评委：{{ r.substituteJudgeName }}
              </div>
              <ElButton v-if="isAdmin" link type="primary"
                class="mt-1 p-0" @click="router.push('/recusals')">
                前往审批处理
              </ElButton>
            </div>
          </div>
        </ElCard>
      </div>
    </div>

    <!-- 管理员受控原稿对话框（查看动作已被后端审计；关闭即清空，浏览器后退缓存也不残留原文） -->
    <ElDialog v-model="rawDialog" title="受控原稿（查看已记录审计）" width="720px"
      @closed="rawScript = null">
      <div v-loading="rawLoading">
        <ElAlert
          title="此处为原稿，包含完整作者、来源机构、联系方式与正文。本次查看已记录“谁在何时查看过原稿”。"
          type="error" :closable="false" show-icon class="mb-4"
        />
        <template v-if="rawScript">
          <div class="space-y-3 text-sm">
            <div class="flex"><span class="w-24 text-gray-500">标题</span><span class="font-medium">{{ rawScript.title }}</span></div>
            <div class="flex"><span class="w-24 text-gray-500">作者</span><span class="text-red-700 font-medium">{{ rawScript.author || '未填写' }}</span></div>
            <div class="flex"><span class="w-24 text-gray-500">来源机构</span><span class="text-red-700 font-medium">{{ rawScript.source || '未填写' }}</span></div>
            <div class="flex flex-col">
              <span class="w-24 text-gray-500">联系方式</span>
              <p class="mt-1 whitespace-pre-wrap text-red-700 bg-red-50 p-2 rounded">{{ rawScript.contactInfo || '未填写' }}</p>
            </div>
            <div class="flex flex-col">
              <span class="w-24 text-gray-500">正文原文</span>
              <p class="mt-1 whitespace-pre-wrap">{{ rawScript.description || '无' }}</p>
            </div>
            <div class="flex flex-col">
              <span class="w-24 text-gray-500">附件说明原文</span>
              <p class="mt-1 whitespace-pre-wrap">{{ rawScript.attachmentNote || '无' }}</p>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <ElButton @click="rawDialog = false">关闭</ElButton>
        <ElButton type="warning" @click="openMap">前往代号映射核对</ElButton>
      </template>
    </ElDialog>

    <!-- 管理员代号映射核对对话框：原始内容 / 识别线索 / 脱敏结果 三区分明；关闭即清空 -->
    <ElDialog v-model="mapDialog" title="原稿—代号映射核对" width="860px" top="6vh"
      @closed="identityMap = null">
      <div v-loading="mapLoading">
        <template v-if="identityMap">
          <ElAlert
            title="核对结论只调整脱敏视图与代号映射，不会改写原稿。评委在核对完成前看到的始终是保守隐藏结果。"
            type="warning" :closable="false" show-icon class="mb-4"
          />

          <!-- 第一区：原始内容 -->
          <ElCard shadow="never" class="mb-4 border-red-200">
            <template #header><span class="text-red-700 font-medium">① 原始内容（原稿）</span></template>
            <div class="space-y-2 text-sm">
              <div class="flex"><span class="w-24 text-gray-500">标题</span><span>{{ identityMap.raw.title }}</span></div>
              <div class="flex"><span class="w-24 text-gray-500">作者</span><span class="text-red-700">{{ identityMap.raw.author || '未填写' }}</span></div>
              <div class="flex"><span class="w-24 text-gray-500">来源机构</span><span class="text-red-700">{{ identityMap.raw.source || '未填写' }}</span></div>
              <div class="flex flex-col">
                <span class="w-24 text-gray-500">联系方式</span>
                <p class="mt-1 whitespace-pre-wrap text-red-700 bg-red-50 p-2 rounded">{{ identityMap.raw.contactInfo || '未填写' }}</p>
              </div>
              <div class="flex flex-col">
                <span class="w-24 text-gray-500">正文</span>
                <p class="mt-1 whitespace-pre-wrap">{{ identityMap.raw.description || '无' }}</p>
              </div>
              <div class="flex flex-col" v-if="identityMap.raw.attachmentNote">
                <span class="w-24 text-gray-500">附件说明</span>
                <p class="mt-1 whitespace-pre-wrap">{{ identityMap.raw.attachmentNote }}</p>
              </div>
            </div>
          </ElCard>

          <!-- 第二区：识别出的线索与身份分组 -->
          <ElCard shadow="never" class="mb-4">
            <template #header>
              <span class="font-medium">② 识别出的线索与代号（{{ identityMap.groups.length }} 个身份，待核对 {{ identityMap.pendingCount }} 处）</span>
            </template>
            <div v-if="identityMap.groups.length === 0"><ElEmpty description="未识别到身份线索" :image-size="60" /></div>
            <div v-else class="space-y-3">
              <div v-for="g in identityMap.groups" :key="g.id"
                class="border rounded-lg p-3"
                :class="g.status === 'PENDING' ? 'border-yellow-300 bg-yellow-50'
                  : g.status === 'CONFIRMED_CHARACTER' ? 'border-green-200 bg-green-50' : 'border-blue-200 bg-blue-50'">
                <div class="flex items-center justify-between flex-wrap gap-2">
                  <div class="text-sm">
                    <ElTag size="small" :type="g.status === 'PENDING' ? 'warning'
                      : g.status === 'CONFIRMED_CHARACTER' ? 'success' : 'primary'">
                      {{ g.statusLabel }}
                    </ElTag>
                    <span class="ml-2 font-medium">{{ g.clueTypeLabel }} · {{ g.representative }}</span>
                    <span class="ml-2 text-xs text-gray-500">{{ g.clueTypeLabel }}代号：{{ g.code }}</span>
                  </div>
                  <div class="flex gap-2">
                    <ElButton size="small" type="primary" :loading="decidingGroupId === g.id"
                      @click="decide(g.id, 'CONFIRMED_IDENTITY')">
                      确认为投稿身份
                    </ElButton>
                    <ElButton size="small" type="success" :loading="decidingGroupId === g.id"
                      @click="decide(g.id, 'CONFIRMED_CHARACTER')">
                      确认为剧情人物
                    </ElButton>
                  </div>
                </div>
                <div class="mt-2 text-xs text-gray-500">
                  共 {{ g.clueCount }} 处出现：
                  <span v-for="c in g.clues" :key="c.id">
                    <ElTag size="small" effect="plain" class="mr-1">{{ c.fieldLabel }}：{{ c.matchedText }}</ElTag>
                  </span>
                  <span class="block mt-1">识别依据：{{ g.detectReasonLabel }}</span>
                  <span v-if="g.decidedByName" class="block mt-1 text-gray-400">
                    最近核对：{{ g.decidedByName }} · {{ g.decidedAt }}
                  </span>
                </div>
              </div>
            </div>
          </ElCard>

          <!-- 第三区：脱敏结果 -->
          <ElCard shadow="never" class="mb-4 border-blue-200">
            <template #header><span class="text-blue-700 font-medium">③ 评委/查看员看到的脱敏结果</span></template>
            <div class="space-y-2 text-sm">
              <div class="flex"><span class="w-24 text-gray-500">标题</span><span>{{ identityMap.masked.title }}</span></div>
              <div class="flex"><span class="w-24 text-gray-500">投稿身份</span><span>{{ identityMap.masked.authorCode || '（无作者字段）' }}</span></div>
              <div class="flex"><span class="w-24 text-gray-500">来源机构</span><span>{{ identityMap.masked.sourceCode || '（无机构线索）' }}</span></div>
              <div class="flex flex-col">
                <span class="w-24 text-gray-500">正文</span>
                <p class="mt-1 whitespace-pre-wrap">{{ identityMap.masked.description || '无' }}</p>
              </div>
              <div class="flex flex-col" v-if="identityMap.masked.attachmentNote">
                <span class="w-24 text-gray-500">附件说明</span>
                <p class="mt-1 whitespace-pre-wrap">{{ identityMap.masked.attachmentNote }}</p>
              </div>
            </div>
          </ElCard>

          <!-- 审计轨迹 -->
          <ElCard shadow="never">
            <template #header><span class="font-medium">查看/核对审计（最近 {{ identityMap.recentAudits.length }} 条）</span></template>
            <div v-if="identityMap.recentAudits.length === 0" class="text-xs text-gray-400">暂无记录</div>
            <ul v-else class="text-xs text-gray-600 space-y-1">
              <li v-for="a in identityMap.recentAudits" :key="a.id">
                {{ a.createdAt }} · {{ a.viewerName }} · {{ a.actionLabel }}
                <span v-if="a.ipAddress">（{{ a.ipAddress }}）</span>
                <span v-if="a.detail">：{{ a.detail }}</span>
              </li>
            </ul>
          </ElCard>
        </template>
      </div>
      <template #footer>
        <ElButton @click="mapDialog = false">关闭</ElButton>
      </template>
    </ElDialog>

    <!-- 申报回避对话框 -->
    <ElDialog v-model="recusalDialog" title="申报利益冲突回避" width="540px">
      <ElForm :model="recusalForm" label-width="110px">
        <ElFormItem v-if="currentUser?.role === 'ADMIN'" label="评委" required>
          <ElSelect v-model="recusalForm.judgeId" placeholder="选择需回避的评委">
            <ElOption v-for="j in judges" :key="j.id"
              :label="`${j.name}（${j.expertise || '无专长'}）`" :value="j.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-else label="申报评委">
          <span class="text-sm text-gray-600">{{ myJudge?.name || '当前评委' }}</span>
        </ElFormItem>
        <ElFormItem label="回避类型" required>
          <ElSelect v-model="recusalForm.type">
            <ElOption label="与作者存在利益关系" value="AUTHOR" />
            <ElOption label="与来源机构存在利益关系" value="ORGANIZATION" />
            <ElOption label="与题材存在利益关系" value="TOPIC" />
            <ElOption label="其他利益关系" value="OTHER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="回避说明" required>
          <ElInput v-model="recusalForm.reason" type="textarea" :rows="3"
            placeholder="请如实描述与作者、来源机构或题材之间的利益关系" />
        </ElFormItem>
        <ElFormItem label="可核验依据" required>
          <ElInput v-model="recusalForm.evidence" type="textarea" :rows="3"
            placeholder="请提供可核验的依据，如任职证明、合同编号、亲属关系说明等（评委侧脚本不会暴露作者身份，请依据脱敏代号申报）" />
        </ElFormItem>
      </ElForm>
      <p class="text-xs text-gray-400">
        提交后脚本仍可查看，但在管理员处理完成前您不能提交新评分；批准回避后历史评分将标记无效并保留审计。
      </p>
      <template #footer>
        <ElButton @click="recusalDialog = false">取消</ElButton>
        <ElButton type="warning" @click="submitRecusal">提交回避申请</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
