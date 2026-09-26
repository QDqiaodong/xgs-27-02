<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  ElButton, ElTable, ElTableColumn, ElTag, ElDialog, ElForm, ElFormItem,
  ElInput, ElMessage, ElTimeline, ElTimelineItem, ElEmpty
} from 'element-plus'
import { getMyRecusals, supplementRecusal, getRecusalLogs } from '@/api/recusal'
import type { RecusalApplicationResponse, RecusalReviewLogResponse } from '@/types'

const applications = ref<RecusalApplicationResponse[]>([])
const loading = ref(false)

const statusLabelMap: Record<string, string> = {
  PENDING: '待处理',
  SUPPLEMENT_REQUESTED: '待补充',
  APPROVED: '已批准',
  REJECTED: '已驳回'
}
const statusTagMap: Record<string, 'warning' | 'primary' | 'success' | 'danger'> = {
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

const loadData = async () => {
  loading.value = true
  try {
    applications.value = await getMyRecusals()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '加载失败')
  } finally {
    loading.value = false
  }
}

// 详情（本人可查看完整敏感内容）
const detailDialog = ref(false)
const detailTarget = ref<RecusalApplicationResponse | null>(null)
const openDetail = (row: RecusalApplicationResponse | any) => {
  detailTarget.value = row
  detailDialog.value = true
}

// 补充材料
const supplementDialog = ref(false)
const supplementTarget = ref<RecusalApplicationResponse | null>(null)
const supplementInfo = ref('')
const openSupplement = (row: RecusalApplicationResponse | any) => {
  supplementTarget.value = row
  supplementInfo.value = ''
  supplementDialog.value = true
}
const submitSupplement = async () => {
  if (!supplementTarget.value) return
  if (!supplementInfo.value.trim()) {
    ElMessage.warning('请填写补充材料')
    return
  }
  try {
    await supplementRecusal(supplementTarget.value.id, supplementInfo.value.trim())
    ElMessage.success('补充材料已提交')
    supplementDialog.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '提交失败')
  }
}

// 轨迹
const logsDialog = ref(false)
const reviewLogs = ref<RecusalReviewLogResponse[]>([])
const openLogs = async (row: RecusalApplicationResponse | any) => {
  logsDialog.value = true
  reviewLogs.value = []
  try {
    reviewLogs.value = await getRecusalLogs(row.id)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '加载处理记录失败')
  }
}

const actionLabelMap: Record<string, string> = {
  CREATE: '提交申请',
  SUPPLEMENT: '补充材料',
  REQUEST_SUPPLEMENT: '要求补充',
  APPROVE: '批准回避',
  REJECT: '驳回申请',
  SUBSTITUTE: '指定替补'
}

onMounted(loadData)
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">我的回避申请</h2>
        <p class="text-gray-500 mt-1">在已分配脚本上申报利益冲突；申请处理期间不能提交评分，脚本仍可查看</p>
      </div>
      <ElButton @click="loadData">刷新</ElButton>
    </div>

    <ElTable :data="applications" stripe v-loading="loading" row-key="id">
      <ElTableColumn prop="id" label="ID" width="60" />
      <ElTableColumn label="脚本" min-width="160">
        <template #default="{ row }">
          <div class="font-medium">{{ row.scriptTitle }}</div>
          <div class="text-xs text-gray-400">#{{ row.scriptId }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="回避类型" width="170">
        <template #default="{ row }">{{ typeLabelMap[row.type] }}</template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="100">
        <template #default="{ row }">
          <ElTag :type="statusTagMap[row.status]">{{ statusLabelMap[row.status] }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="处理人" width="100">
        <template #default="{ row }">{{ row.decidedByName || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="提交时间" width="170" />
      <ElTableColumn label="替补接管" width="110">
        <template #default="{ row }">
          <span v-if="row.substituteJudgeName" class="text-green-600">{{ row.substituteJudgeName }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
          <ElButton v-if="row.status === 'SUPPLEMENT_REQUESTED'" link type="warning" @click="openSupplement(row)">
            补充材料
          </ElButton>
          <ElButton link type="info" @click="openLogs(row)">处理轨迹</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog v-model="detailDialog" :title="`回避申请 #${detailTarget?.id ?? ''}`" width="600px">
      <div v-if="detailTarget" class="space-y-3 text-sm">
        <div class="grid grid-cols-2 gap-2">
          <div><span class="text-gray-500">脚本：</span>{{ detailTarget.scriptTitle }}</div>
          <div><span class="text-gray-500">类型：</span>{{ typeLabelMap[detailTarget.type] }}</div>
          <div>
            <span class="text-gray-500">状态：</span>
            <ElTag size="small" :type="statusTagMap[detailTarget.status]">
              {{ statusLabelMap[detailTarget.status] }}
            </ElTag>
          </div>
          <div><span class="text-gray-500">处理人：</span>{{ detailTarget.decidedByName || '—' }}</div>
        </div>
        <div class="bg-gray-50 rounded p-3">
          <div class="text-gray-500 mb-1">回避说明</div>
          <p class="whitespace-pre-wrap">{{ detailTarget.reason }}</p>
        </div>
        <div class="bg-gray-50 rounded p-3">
          <div class="text-gray-500 mb-1">可核验依据</div>
          <p class="whitespace-pre-wrap">{{ detailTarget.evidence }}</p>
        </div>
        <div v-if="detailTarget.supplementInfo" class="bg-gray-50 rounded p-3">
          <div class="text-gray-500 mb-1">我的补充材料</div>
          <p class="whitespace-pre-wrap">{{ detailTarget.supplementInfo }}</p>
        </div>
        <div v-if="detailTarget.decisionComment" class="bg-yellow-50 rounded p-3">
          <div class="text-gray-500 mb-1">管理员处理意见</div>
          <p class="whitespace-pre-wrap">{{ detailTarget.decisionComment }}</p>
        </div>
        <p v-if="detailTarget.status === 'APPROVED'" class="text-xs text-orange-600">
          回避已批准：您在此脚本上的分配已终止，历史评分已标记无效并保留供审计，该脚本将由替补评委接管。
        </p>
      </div>
    </ElDialog>

    <ElDialog v-model="supplementDialog" title="提交补充材料" width="520px">
      <ElForm label-width="90px">
        <ElFormItem label="补充材料" required>
          <ElInput v-model="supplementInfo" type="textarea" :rows="5"
            placeholder="按管理员要求补充可核验的材料说明" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="supplementDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitSupplement">提交</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="logsDialog" title="处理轨迹" width="560px">
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
  </div>
</template>
