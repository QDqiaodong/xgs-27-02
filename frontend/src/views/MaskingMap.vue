<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElButton, ElCard, ElTag, ElMessage, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElAlert
} from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getMaskingMap, confirmClue, addClue } from '@/api/masking'
import type { MaskingMapResponse, MaskingClueItem, ClueType, ClueFieldOrigin } from '@/types'

const route = useRoute()
const router = useRouter()
const scriptId = Number(route.params.id)

const mapping = ref<MaskingMapResponse | null>(null)
const loading = ref(false)

const clueTypeLabels: Record<string, string> = {
  NAME: '姓名', PHONE: '电话', EMAIL: '邮箱', ORG: '机构全称', SOCIAL: '社交账号'
}
const fieldLabels: Record<string, string> = {
  AUTHOR: '作者字段', SOURCE: '来源字段', TITLE: '标题', DESCRIPTION: '正文描述', ATTACHMENT: '附件说明'
}
const statusMeta: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  CONFIRMED: { label: '已确认·投稿身份', type: 'success' },
  PENDING: { label: '待管理员核对', type: 'warning' },
  CHARACTER: { label: '正文角色·保留', type: 'info' }
}

const pendingClues = computed(() => (mapping.value?.clues || []).filter(c => c.status === 'PENDING'))
const resolvedClues = computed(() => (mapping.value?.clues || []).filter(c => c.status !== 'PENDING'))

const loadMapping = async () => {
  loading.value = true
  try {
    mapping.value = await getMaskingMap(scriptId)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '加载脱敏映射失败')
  } finally {
    loading.value = false
  }
}

// ===================== 核对 =====================
const confirmDialog = ref(false)
const confirmTarget = ref<MaskingClueItem | null>(null)
const confirmDecision = ref<'IDENTITY' | 'CHARACTER'>('IDENTITY')
const confirmIdentityId = ref<number | null>(null)

const openConfirm = (clue: MaskingClueItem, decision: 'IDENTITY' | 'CHARACTER') => {
  confirmTarget.value = clue
  confirmDecision.value = decision
  confirmIdentityId.value = mapping.value?.identities.find(i => i.kind === 'SUBMITTER')?.id ?? null
  confirmDialog.value = true
}

const submitConfirm = async () => {
  const clue = confirmTarget.value
  if (!clue) return
  try {
    mapping.value = await confirmClue(
      scriptId, clue.id, confirmDecision.value,
      confirmDecision.value === 'IDENTITY' ? (confirmIdentityId.value ?? undefined) : undefined
    )
    ElMessage.success('核对完成：脱敏视图与映射已更新，原稿保持不变')
    confirmDialog.value = false
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '核对失败')
  }
}

// ===================== 手工补充线索 =====================
const addDialog = ref(false)
const addForm = ref<{ clueType: ClueType; fieldOrigin: ClueFieldOrigin; originalText: string }>({
  clueType: 'NAME', fieldOrigin: 'DESCRIPTION', originalText: ''
})

const submitAdd = async () => {
  if (!addForm.value.originalText.trim()) {
    ElMessage.warning('请填写线索原文')
    return
  }
  try {
    mapping.value = await addClue(scriptId, {
      clueType: addForm.value.clueType,
      fieldOrigin: addForm.value.fieldOrigin,
      originalText: addForm.value.originalText.trim()
    })
    ElMessage.success('线索已补充并确认为投稿身份')
    addDialog.value = false
    addForm.value = { clueType: 'NAME', fieldOrigin: 'DESCRIPTION', originalText: '' }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '补充线索失败')
  }
}

onMounted(loadMapping)
</script>

<template>
  <div class="space-y-6" v-loading="loading">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">脱敏映射核对</h2>
        <p class="text-gray-500 mt-1">
          脚本 #{{ scriptId }} 的原稿 / 线索 / 脱敏结果对照（仅管理员可见，本次查看已记录审计）
        </p>
      </div>
      <div class="flex gap-2">
        <ElButton type="primary" @click="addDialog = true">手工补充线索</ElButton>
        <ElButton @click="router.push(`/script/${scriptId}`)">返回详情</ElButton>
      </div>
    </div>

    <ElAlert v-if="mapping && mapping.pendingCount > 0" type="warning" :closable="false"
      :title="`有 ${mapping.pendingCount} 条线索待核对：评委当前看到的是更保守的隐藏结果【待核对】，核对完成前不会泄露可疑片段`" />
    <ElAlert v-else-if="mapping" type="success" :closable="false"
      title="全部线索已核对，评委看到的是稳定的脱敏视图" />

    <template v-if="mapping">
      <!-- 身份与代号 -->
      <ElCard shadow="never">
        <template #header><span class="font-semibold">身份与稳定代号（同一脚本内一致，跨脚本不可横向关联）</span></template>
        <div class="flex flex-wrap gap-3">
          <div v-for="identity in mapping.identities" :key="identity.id"
            class="border rounded-lg px-4 py-2 bg-gray-50">
            <span class="font-mono font-medium text-primary">{{ identity.aliasCode }}</span>
            <span class="text-xs text-gray-400 ml-2">
              {{ identity.kind === 'SUBMITTER' ? '投稿人' : '相关身份' }}
            </span>
          </div>
          <span v-if="mapping.identities.length === 0" class="text-gray-400 text-sm">暂无意身份</span>
        </div>
      </ElCard>

      <!-- 待核对线索 -->
      <ElCard v-if="pendingClues.length > 0" shadow="never">
        <template #header>
          <span class="font-semibold text-yellow-600">待核对线索（{{ pendingClues.length }}）</span>
        </template>
        <div class="space-y-3">
          <div v-for="clue in pendingClues" :key="clue.id"
            class="border border-yellow-200 bg-yellow-50 rounded-lg p-3 flex items-center justify-between">
            <div>
              <ElTag size="small" type="warning">{{ clueTypeLabels[clue.clueType] }}</ElTag>
              <ElTag size="small" class="ml-1" type="info">{{ fieldLabels[clue.fieldOrigin] }}</ElTag>
              <span class="ml-2 font-mono">{{ clue.originalText }}</span>
              <div class="text-xs text-gray-400 mt-1">评委当前看到：【待核对】（保守隐藏）</div>
            </div>
            <div class="flex gap-2 shrink-0">
              <ElButton size="small" type="success" @click="openConfirm(clue, 'IDENTITY')">
                属于投稿身份
              </ElButton>
              <ElButton size="small" type="info" @click="openConfirm(clue, 'CHARACTER')">
                属于正文角色
              </ElButton>
            </div>
          </div>
        </div>
      </ElCard>

      <!-- 已识别线索全表 -->
      <ElCard shadow="never">
        <template #header><span class="font-semibold">全部识别线索（{{ mapping.clues.length }}）</span></template>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-gray-500 border-b">
                <th class="py-2 pr-4">类型</th>
                <th class="py-2 pr-4">来源位置</th>
                <th class="py-2 pr-4">线索原文</th>
                <th class="py-2 pr-4">状态</th>
                <th class="py-2 pr-4">脱敏结果</th>
                <th class="py-2 pr-4">核对人/时间</th>
                <th class="py-2">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="clue in resolvedClues" :key="clue.id" class="border-b last:border-0">
                <td class="py-2 pr-4">{{ clueTypeLabels[clue.clueType] }}</td>
                <td class="py-2 pr-4">{{ fieldLabels[clue.fieldOrigin] }}</td>
                <td class="py-2 pr-4 font-mono">{{ clue.originalText }}</td>
                <td class="py-2 pr-4">
                  <ElTag size="small" :type="statusMeta[clue.status].type">
                    {{ statusMeta[clue.status].label }}
                  </ElTag>
                </td>
                <td class="py-2 pr-4 font-mono">
                  <span v-if="clue.status === 'CONFIRMED'" class="text-primary">{{ clue.aliasCode }}</span>
                  <span v-else-if="clue.status === 'CHARACTER'" class="text-gray-500">保留原文</span>
                  <span v-else class="text-yellow-600">【待核对】</span>
                </td>
                <td class="py-2 pr-4 text-xs text-gray-400">
                  <template v-if="clue.confirmedByName">{{ clue.confirmedByName }} · {{ clue.confirmedAt }}</template>
                  <template v-else>自动识别</template>
                </td>
                <td class="py-2">
                  <ElButton v-if="clue.status === 'CHARACTER'" size="small" link type="primary"
                    @click="openConfirm(clue, 'IDENTITY')">改判为投稿身份</ElButton>
                  <ElButton v-else-if="clue.status === 'CONFIRMED'" size="small" link type="info"
                    @click="openConfirm(clue, 'CHARACTER')">改判为正文角色</ElButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </ElCard>

      <!-- 原稿 vs 脱敏结果 -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <ElCard shadow="never">
          <template #header>
            <span class="font-semibold text-red-600">原稿（仅管理员可见）</span>
          </template>
          <div class="space-y-3 text-sm">
            <div><span class="text-gray-500">标题：</span>{{ mapping.original.title }}</div>
            <div><span class="text-gray-500">作者：</span>{{ mapping.original.author || '未填写' }}</div>
            <div><span class="text-gray-500">来源机构：</span>{{ mapping.original.source || '未填写' }}</div>
            <div>
              <span class="text-gray-500">正文描述：</span>
              <p class="mt-1 whitespace-pre-wrap">{{ mapping.original.description || '无' }}</p>
            </div>
            <div>
              <span class="text-gray-500">附件说明：</span>
              <p class="mt-1 whitespace-pre-wrap">{{ mapping.original.attachmentNote || '无' }}</p>
            </div>
          </div>
        </ElCard>
        <ElCard shadow="never">
          <template #header>
            <span class="font-semibold text-green-600">脱敏结果（评委/查看员实际看到）</span>
          </template>
          <div class="space-y-3 text-sm">
            <div><span class="text-gray-500">标题：</span>{{ mapping.masked.title }}</div>
            <div><span class="text-gray-500">作者：</span>{{ mapping.masked.author || '未填写' }}</div>
            <div><span class="text-gray-500">来源机构：</span>{{ mapping.masked.source || '未填写' }}</div>
            <div>
              <span class="text-gray-500">正文描述：</span>
              <p class="mt-1 whitespace-pre-wrap">{{ mapping.masked.description || '无' }}</p>
            </div>
            <div>
              <span class="text-gray-500">附件说明：</span>
              <p class="mt-1 whitespace-pre-wrap">{{ mapping.masked.attachmentNote || '无' }}</p>
            </div>
          </div>
        </ElCard>
      </div>
    </template>

    <!-- 核对确认对话框 -->
    <ElDialog v-model="confirmDialog" title="核对线索" width="480px">
      <template v-if="confirmTarget">
        <p class="text-sm text-gray-600 mb-3">
          线索 <span class="font-mono font-medium">{{ confirmTarget.originalText }}</span>
          （{{ clueTypeLabels[confirmTarget.clueType] }} · {{ fieldLabels[confirmTarget.fieldOrigin] }}）
        </p>
        <ElForm label-width="110px">
          <ElFormItem label="核对结论">
            <ElSelect v-model="confirmDecision">
              <ElOption label="属于投稿身份（替换为稳定代号）" value="IDENTITY" />
              <ElOption label="属于正文角色（保留原文）" value="CHARACTER" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="confirmDecision === 'IDENTITY'" label="归属身份">
            <ElSelect v-model="confirmIdentityId" placeholder="缺省归属投稿人">
              <ElOption v-for="identity in mapping?.identities || []" :key="identity.id"
                :label="`${identity.aliasCode}（${identity.kind === 'SUBMITTER' ? '投稿人' : '相关身份'}）`"
                :value="identity.id" />
              <ElOption label="＋ 新建相关身份（与投稿人不同的另一身份，分配独立代号）" :value="-1" />
            </ElSelect>
          </ElFormItem>
        </ElForm>
        <p class="text-xs text-gray-400">核对只调整脱敏视图与映射，不会改写原稿；操作将写入审计记录。</p>
      </template>
      <template #footer>
        <ElButton @click="confirmDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitConfirm">确认核对</ElButton>
      </template>
    </ElDialog>

    <!-- 手工补充线索对话框 -->
    <ElDialog v-model="addDialog" title="手工补充线索" width="480px">
      <ElForm label-width="110px">
        <ElFormItem label="线索类型" required>
          <ElSelect v-model="addForm.clueType">
            <ElOption v-for="(label, value) in clueTypeLabels" :key="value" :label="label" :value="value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="来源位置" required>
          <ElSelect v-model="addForm.fieldOrigin">
            <ElOption v-for="(label, value) in fieldLabels" :key="value" :label="label" :value="value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="线索原文" required>
          <ElInput v-model="addForm.originalText" placeholder="必须与所选字段中的原文完全一致" />
        </ElFormItem>
      </ElForm>
      <p class="text-xs text-gray-400">补充的线索将直接确认为投稿身份并替换为稳定代号；操作将写入审计记录。</p>
      <template #footer>
        <ElButton @click="addDialog = false">取消</ElButton>
        <ElButton type="primary" @click="submitAdd">补充并确认</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
