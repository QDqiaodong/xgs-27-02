<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElButton, ElTable, ElTableColumn, ElTag, ElDialog, ElForm, ElFormItem,
  ElInput, ElSelect, ElOption, ElDatePicker, ElMessage, ElRadioGroup, ElRadio,
  ElTimeline, ElTimelineItem, ElEmpty
} from 'element-plus'
import {
  getAllRecusals, decideRecusal, assignSubstitute, getSubstituteCandidates,
  getRecusalLogs, createRecusal
} from '@/api/recusal'
import { getScripts } from '@/api/script'
import { getJudges } from '@/api/judge'
import type {
  RecusalApplicationResponse, RecusalDecision, RecusalReviewLogResponse,
  SubstituteCandidateResponse, ScriptResponse, JudgeResponse, RecusalType
} from '@/types'

const applications = ref<RecusalApplicationResponse[]>([])
const loading = ref(false)

const statusFilter = ref('')
const filters = ['', 'PENDING', 'SUPPLEMENT_REQUESTED', 'APPROVED', 'REJECTED']

const statusLabelMap: Record<string, string> = {
  PENDING: '待处理',
  SUPPLEMENT_REQUESTED: '待补充',
  APPROVED: '已批准',
  REJECTED: '已驳回'
}
const statusTagMap: Record<string, 'warning' | 'primary' | 'success' | 'danger' | 'info'> = {
  PENDING: 'warning',
  SUPPLEMENT_REQUESTED: 'primary',
  APPROVED: 'success',
  REJECTED: 'danger'
}
const typeLabelMap: Record<string, string> = {
  AUTHOR: '与作者存在利益关系',
  ORGANIZATION: '与来源机构存在利益关系',
  TOPIC: '与题材存在利益关系',
  OTHER: '其他利益关系'
}
const actionLabelMap: Record<string, string> = {
  CREATE: '提交申请',
  SUPPLEMENT: '补充材料',
  REQUEST_SUPPLEMENT: '要求补充',
  APPROVE: '批准回避',
  REJECT: '驳回申请',
  SUBSTITUTE: '指定替补'
}

const filteredApplications = computed(() =>
  statusFilter.value ? applications.value.filter(a => a.status === statusFilter.value) : applications.value
)

const loadData = async () => {
  loading.value = true
  try {
    applications.value = await getAllRecusals()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '加载回避申请失败')
  } finally {
    loading.value = false
  }
}

// ===================== 审批对话框 =====================
const decisionDialog = ref(false)
const decisionTarget = ref<RecusalApplicationResponse | null>(null)
const decision = ref<RecusalDecision>('APPROVE')
const decisionComment = ref('')

const openDecision = (row: RecusalApplicationResponse | any, preset: RecusalDecision) => {
  decisionTarget.value = row
  decision.value = preset
  decisionComment.value = ''
  decisionDialog.value = true
}

const submitDecision = async () => {
  if (!decisionTarget.value) return
  if (!decisionComment.value.trim()) {
    ElMessage.warning('必须填写处理理由')
    return
  }
  try {
    await decideRecusal(decisionTarget.value.id, decision.value, decisionComment.value.trim())
    ElMessage.success('处理完成')
    decisionDialog.value = false
    loadData()
  } catch (error: any) {
    const status = error?.response?.status
    ElMessage.error(error?.response?.data?.error ||
      (status === 409 ? '该申请已被其他管理员处理，请刷新列表' : '处理失败'))
    if (status === 409) {
      decisionDialog.value = false
      loadData()
    }
  }
}

// ===================== 替补对话框 =====================
const substituteDialog = ref(false)
const substituteTarget = ref<RecusalApplicationResponse | null>(null)
const candidates = ref<SubstituteCandidateResponse[]>([])
const substituteJudgeId = ref<number>(0)
const substituteDeadline = ref('')

const openSubstitute = async (row: RecusalApplicationResponse | any) => {
  substituteTarget.value = row
  substituteJudgeId.value = 0
  substituteDeadline.value = ''
  substituteDialog.value = true
  try {
    candidates.value = await getSubstituteCandidates(row.id)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '加载替补候选失败')
  }
}

const submitSubstitute = async () => {
  if (!substituteTarget.value) return
  if (!substituteJudgeId.value) {
    ElMessage.warning('请选择替补评委')
    return
  }
  try {
    await assignSubstitute(
      substituteTarget.value.id,
      substituteJudgeId.value,
      substituteDeadline.value || undefined
    )
    ElMessage.success('替补评委已接管')
    substituteDialog.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '替补接管失败')
  }
}

// ===================== 审计轨迹对话框 =====================
const logsDialog = ref(false)
const logsTarget = ref<RecusalApplicationResponse | null>(null)
const reviewLogs = ref<RecusalReviewLogResponse[]>([])

const openLogs = async (row: RecusalApplicationResponse | any) => {
  logsTarget.value = row
  logsDialog.value = true
  try {
    reviewLogs.value = await getRecusalLogs(row.id)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '加载处理记录失败')
  }
}

// ===================== 代为申报对话框 =====================
const createDialog = ref(false)
const scripts = ref<ScriptResponse[]>([])
const judges = ref<JudgeResponse[]>([])
const createForm = ref({
  scriptId: 0,
  judgeId: 0,
  type: 'AUTHOR' as RecusalType,
  reason: '',
  evidence: ''
})

const openCreate = async () => {
  createForm.value = { scriptId: 0, judgeId: 0, type: 'AUTHOR', reason: '', evidence: '' }
  createDialog.value = true
  try {
    const [scriptPage, judgeList] = await Promise.all([getScripts({ page: 0, size: 200 }), getJudges()])
    scripts.value = (scriptPage as any).content || []
    judges.value = judgeList.filter((j: JudgeResponse) => j.status === 'ACTIVE')
  } catch {
    ElMessage.error('加载脚本/评委失败')
  }
}

const submitCreate = async () => {
  const f = createForm.value
  if (!f.scriptId || !f.judgeId || !f.reason.trim() || !f.evidence.trim()) {
    ElMessage.warning('请完整填写回避类型、说明与可核验依据')
    return
  }
  try {
    await createRecusal({
      scriptId: f.scriptId,
      judgeId: f.judgeId,
      type: f.type,
      reason: f.reason.trim(),
      evidence: f.evidence.trim()
    })
    ElMessage.success('已代为提交回避申请')
    createDialog.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '提交失败')
  }
}

onMounted(loadData)
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">回避申请审批</h2>
        <p class="text-gray-500 mt-1">批准/驳回/要求补充，批准后指定替补评委接管；不能审批自己代为提交的申请</p>
      </div>
      <div class="flex gap-2">
        <ElSelect v-model="statusFilter" placeholder="全部状态" style="width: 140px">
          <ElOption v-for="f in filters" :key="f" :label="f ? statusLabelMap[f] : '全部状态'" :value="f" />
        </ElSelect>
        <ElButton type="primary" @click="openCreate">代评委申报</ElButton>
        <ElButton @click="loadData">刷新</ElButton>
      </div>
    </div>

    <ElTable :data="filteredApplications" stripe v-loading="loading" row-key="id">
      <ElTableColumn prop="id" label="ID" width="60" />
      <ElTableColumn label="脚本" min-width="150">
        <template #default="{ row }">
          <div class="font-medium">{{ row.scriptTitle }}</div>
          <div class="text-xs text-gray-400">#{{ row.scriptId }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="judgeName" label="申报评委" width="100" />
      <ElTableColumn label="回避类型" width="160">
        <template #default="{ row }">{{ typeLabelMap[row.type] }}</template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="100">
        <template #default="{ row }">
          <ElTag :type="statusTagMap[row.status]">{{ statusLabelMap[row.status] }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="提交人" width="130">
        <template #default="{ row }">
          {{ row.submittedByName }}
          <ElTag v-if="row.submittedOnBehalf" size="small" type="info" class="ml-1">代提交</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="decidedByName" label="处理人" width="100">
        <template #default="{ row }">{{ row.decidedByName || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="替补接管" width="120">
        <template #default="{ row }">
          <span v-if="row.substituteJudgeName" class="text-green-600">{{ row.substituteJudgeName }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openLogs(row)">轨迹</ElButton>
          <template v-if="row.status === 'PENDING' || row.status === 'SUPPLEMENT_REQUESTED'">
            <ElButton link type="success" @click="openDecision(row, 'APPROVE')">批准</ElButton>
            <ElButton link type="danger" @click="openDecision(row, 'REJECT')">驳回</ElButton>
            <ElButton link type="warning" @click="openDecision(row, 'REQUEST_SUPPLEMENT')">补充</ElButton>
          </template>
          <ElButton
            v-else-if="row.status === 'APPROVED' && !row.substituteJudgeId"
            link type="primary"
            @click="openSubstitute(row)"
          >指定替补</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 审批对话框（含完整敏感内容） -->
    <ElDialog v-model="decisionDialog" :title="`处理回避申请 #${decisionTarget?.id ?? ''}`" width="640px">
      <div v-if="decisionTarget" class="space-y-4">
        <div class="bg-gray-50 rounded-lg p-4 text-sm space-y-2">
          <div><span class="text-gray-500">脚本：</span>{{ decisionTarget.scriptTitle }}</div>
          <div><span class="text-gray-500">评委：</span>{{ decisionTarget.judgeName }}</div>
          <div><span class="text-gray-500">回避类型：</span>{{ typeLabelMap[decisionTarget.type] }}</div>
          <div>
            <span class="text-gray-500">回避说明：</span>
            <span class="whitespace-pre-wrap">{{ decisionTarget.reason }}</span>
          </div>
          <div>
            <span class="text-gray-500">可核验依据：</span>
            <span class="whitespace-pre-wrap">{{ decisionTarget.evidence }}</span>
          </div>
          <div v-if="decisionTarget.supplementInfo">
            <span class="text-gray-500">补充材料：</span>
            <span class="whitespace-pre-wrap">{{ decisionTarget.supplementInfo }}</span>
          </div>
        </div>
        <ElForm label-width="90px">
          <ElFormItem label="处理决定">
            <ElRadioGroup v-model="decision">
              <ElRadio value="APPROVE">批准回避</ElRadio>
              <ElRadio value="REJECT">驳回</ElRadio>
              <ElRadio value="REQUEST_SUPPLEMENT">要求补充材料</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="处理理由" required>
            <ElInput v-model="decisionComment" type="textarea" :rows="3"
              :placeholder="decision === 'REQUEST_SUPPLEMENT' ? '请说明需要补充的内容' : '请填写审批理由（将留存审计）'" />
          </ElFormItem>
        </ElForm>
        <p v-if="decision === 'APPROVE'" class="text-xs text-orange-600">
          批准后将立即终止该评委的分配，其历史评分标记为无效并从总分/等级计算中排除；脚本若已分级将撤销等级进入待补评，之后需指定替补评委。
        </p>
      </div>
      <template #footer>
        <ElButton @click="decisionDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitDecision">确认提交</ElButton>
      </template>
    </ElDialog>

    <!-- 替补对话框 -->
    <ElDialog v-model="substituteDialog" :title="`为回避申请 #${substituteTarget?.id ?? ''} 指定替补`" width="560px">
      <ElForm label-width="110px">
        <ElFormItem label="替补评委" required>
          <ElSelect v-model="substituteJudgeId" placeholder="仅显示未参与该脚本的正常状态评委">
            <ElOption
              v-for="c in candidates"
              :key="c.judgeId"
              :value="c.judgeId"
              :disabled="!c.expertiseMatched"
              :label="`${c.judgeName}（${c.expertise || '无专长记录'}）${c.expertiseMatched ? '' : ' - 方向不匹配'}`"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="评分截止时间">
          <ElDatePicker v-model="substituteDeadline" type="datetime"
            placeholder="选择截止时间" value-format="YYYY-MM-DD HH:mm:ss" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="substituteDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitSubstitute">确认接管</ElButton>
      </template>
    </ElDialog>

    <!-- 审计轨迹 -->
    <ElDialog v-model="logsDialog" :title="`处理轨迹 #${logsTarget?.id ?? ''}`" width="640px">
      <ElTimeline v-if="reviewLogs.length">
        <ElTimelineItem
          v-for="log in reviewLogs"
          :key="log.id"
          :timestamp="`${log.createdAt} · 操作人：${log.operatorName || '系统'}`"
        >
          <div class="font-medium">{{ actionLabelMap[log.action] || log.action }}</div>
          <div class="text-xs text-gray-500">
            {{ log.fromStatus ? statusLabelMap[log.fromStatus] : '—' }}
            → {{ log.toStatus ? statusLabelMap[log.toStatus] : '—' }}
          </div>
          <div v-if="log.comment" class="text-sm mt-1 whitespace-pre-wrap">{{ log.comment }}</div>
        </ElTimelineItem>
      </ElTimeline>
      <ElEmpty v-else description="暂无记录" />
    </ElDialog>

    <!-- 代为申报 -->
    <ElDialog v-model="createDialog" title="代评委提交回避申请" width="560px">
      <ElForm :model="createForm" label-width="100px">
        <ElFormItem label="脚本" required>
          <ElSelect v-model="createForm.scriptId" placeholder="选择脚本">
            <ElOption v-for="s in scripts" :key="s.id" :label="s.title" :value="s.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="评委" required>
          <ElSelect v-model="createForm.judgeId" placeholder="选择已分配该脚本的评委">
            <ElOption v-for="j in judges" :key="j.id"
              :label="`${j.name}（${j.expertise || '无专长'}）`" :value="j.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="回避类型" required>
          <ElSelect v-model="createForm.type">
            <ElOption label="与作者存在利益关系" value="AUTHOR" />
            <ElOption label="与来源机构存在利益关系" value="ORGANIZATION" />
            <ElOption label="与题材存在利益关系" value="TOPIC" />
            <ElOption label="其他利益关系" value="OTHER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="回避说明" required>
          <ElInput v-model="createForm.reason" type="textarea" :rows="3" placeholder="请描述具体利益关系" />
        </ElFormItem>
        <ElFormItem label="可核验依据" required>
          <ElInput v-model="createForm.evidence" type="textarea" :rows="3" placeholder="如合同编号、亲属关系证明、任职证明等" />
        </ElFormItem>
      </ElForm>
      <p class="text-xs text-gray-400">提示：由您代提交的申请，您本人不能审批，需由其他管理员处理。</p>
      <template #footer>
        <ElButton @click="createDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitCreate">提交申请</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
