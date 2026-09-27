<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElSelect, ElOption, ElPagination, ElMessage, ElDialog } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getScripts, deleteScript } from '@/api/script'
import type { ScriptResponse } from '@/types'

const route = useRoute()
const router = useRouter()

const scripts = ref<ScriptResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const statusFilter = ref('')
const gradeFilter = ref('')
const genreFilter = ref('')
const loading = ref(false)
const deleteDialog = ref(false)
const deleteId = ref<number | null>(null)
const currentUser = ref<any>(JSON.parse(localStorage.getItem('user') || 'null'))
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

// 请求序号：只有最新一次请求的响应允许更新列表，
// 加载慢的旧请求晚返回时不得覆盖用户最后选择的筛选状态
let requestSeq = 0

const STATUS_VALUES = ['PENDING', 'REVIEWING', 'PENDING_SUPPLEMENT', 'GRADED']
const GRADE_VALUES = ['S', 'A', 'B', 'C']
const GENRE_VALUES = ['搞笑', '情感', '科普', '生活', '美食', '旅游', '科技', '娱乐', '体育', '教育']
const PAGE_SIZES = [10, 20, 50]
const DEFAULT_SIZE = 10

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待评审', value: 'PENDING' },
  { label: '评审中', value: 'REVIEWING' },
  { label: '待补评', value: 'PENDING_SUPPLEMENT' },
  { label: '已分级', value: 'GRADED' }
]

const gradeOptions = [
  { label: '全部', value: '' },
  { label: 'S级', value: 'S' },
  { label: 'A级', value: 'A' },
  { label: 'B级', value: 'B' },
  { label: 'C级', value: 'C' }
]

const genreOptions = [
  { label: '全部', value: '' },
  ...GENRE_VALUES.map(genre => ({ label: genre, value: genre }))
]

const firstString = (value: unknown): string => {
  if (Array.isArray(value)) return value[0] ?? ''
  return typeof value === 'string' ? value : ''
}

// 从地址栏解析筛选与分页状态，非法值回退为默认，
// 刷新或复制带筛选条件的地址重新打开时恢复同样的条件
const parseRouteQuery = () => {
  const status = firstString(route.query.status)
  const grade = firstString(route.query.grade)
  const genre = firstString(route.query.genre)
  const parsedPage = parseInt(firstString(route.query.page), 10)
  const parsedSize = parseInt(firstString(route.query.size), 10)
  return {
    status: STATUS_VALUES.includes(status) ? status : '',
    grade: GRADE_VALUES.includes(grade) ? grade : '',
    genre: GENRE_VALUES.includes(genre) ? genre : '',
    page: Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1,
    size: PAGE_SIZES.includes(parsedSize) ? parsedSize : DEFAULT_SIZE
  }
}

const applyParsedQuery = (parsed: ReturnType<typeof parseRouteQuery>) => {
  statusFilter.value = parsed.status
  gradeFilter.value = parsed.grade
  genreFilter.value = parsed.genre
  page.value = parsed.page
  size.value = parsed.size
}

// 当前状态对应的规范化查询串（缺省值不写入地址）
const buildQuery = (): Record<string, string> => {
  const query: Record<string, string> = {}
  if (statusFilter.value) query.status = statusFilter.value
  if (gradeFilter.value) query.grade = gradeFilter.value
  if (genreFilter.value) query.genre = genreFilter.value
  if (page.value > 1) query.page = String(page.value)
  if (size.value !== DEFAULT_SIZE) query.size = String(size.value)
  return query
}

const normalizedRouteQuery = (): Record<string, string> => {
  const normalized: Record<string, string> = {}
  for (const key of Object.keys(route.query)) {
    const value = firstString(route.query[key])
    if (value !== '') normalized[key] = value
  }
  return normalized
}

const sameQuery = (a: Record<string, string>, b: Record<string, string>) => {
  const aKeys = Object.keys(a)
  const bKeys = Object.keys(b)
  return aKeys.length === bKeys.length && aKeys.every(key => a[key] === b[key])
}

// 把当前筛选与分页状态写回地址栏（replace，不产生多余历史记录）
const syncUrl = () => {
  const query = buildQuery()
  if (!sameQuery(query, normalizedRouteQuery())) {
    router.replace({ query })
  }
}

const loadScripts = async () => {
  const seq = ++requestSeq
  loading.value = true
  try {
    const res = await getScripts({
      page: page.value - 1,
      size: size.value,
      status: statusFilter.value || undefined,
      grade: gradeFilter.value || undefined,
      genre: genreFilter.value || undefined
    })
    if (seq !== requestSeq) return // 已有更新的请求发出，丢弃这份过期响应
    scripts.value = res.content
    total.value = res.totalElements
    if (res.totalElements > 0 && res.number + 1 !== page.value) {
      // 服务端对越界页回退到最后一个有效页，本地页码与地址跟随实际返回页
      page.value = res.number + 1
      syncUrl()
    } else if (res.totalElements === 0 && page.value !== 1) {
      // 零结果时任何页的内容都相同（空表），页码归位到第一页
      page.value = 1
      syncUrl()
    }
  } catch (error) {
    if (seq !== requestSeq) return
    ElMessage.error('加载脚本列表失败')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

// 筛选条件变化：回到首个有效页再查询，避免停留在已不存在的页码
const handleFilterChange = () => {
  page.value = 1
  syncUrl()
  loadScripts()
}

const handlePageChange = (newPage: number) => {
  page.value = newPage
  syncUrl()
  loadScripts()
}

const handleSizeChange = (newSize: number) => {
  size.value = newSize
  page.value = 1
  syncUrl()
  loadScripts()
}

// 浏览器前进/后退或应用内跳转到带条件的地址时，按地址恢复状态
watch(() => route.query, () => {
  const parsed = parseRouteQuery()
  const unchanged =
    parsed.status === statusFilter.value &&
    parsed.grade === gradeFilter.value &&
    parsed.genre === genreFilter.value &&
    parsed.page === page.value &&
    parsed.size === size.value
  if (unchanged) return
  applyParsedQuery(parsed)
  loadScripts()
})

const handleView = (id: number) => {
  router.push(`/script/${id}`)
}

const handleScore = (id: number) => {
  router.push(`/script/${id}/score`)
}

const handleDelete = (id: number) => {
  deleteId.value = id
  deleteDialog.value = true
}

const confirmDelete = async () => {
  if (deleteId.value === null) return
  try {
    await deleteScript(deleteId.value)
    ElMessage.success('删除成功')
    deleteDialog.value = false
    // 若删除的是末页最后一条，服务端会回退到最后一个有效页
    loadScripts()
  } catch (error) {
    ElMessage.error('删除失败')
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

onMounted(() => {
  applyParsedQuery(parseRouteQuery())
  syncUrl()
  loadScripts()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">脚本列表</h2>
        <p class="text-gray-500 mt-1">列表标题为双盲脱敏视图；原稿作者、来源机构与联系方式不在任何列表/详情响应中</p>
      </div>
      <ElButton v-if="isAdmin" type="primary" @click="router.push('/script/create')">
        录入脚本
      </ElButton>
    </div>

    <div class="bg-white rounded-xl shadow-sm p-6">
      <div class="flex items-center gap-4 mb-6">
        <ElSelect
          v-model="statusFilter"
          placeholder="状态筛选"
          style="width: 150px"
          @change="handleFilterChange"
        >
          <ElOption v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </ElSelect>

        <ElSelect
          v-model="gradeFilter"
          placeholder="等级筛选"
          style="width: 150px"
          @change="handleFilterChange"
        >
          <ElOption v-for="opt in gradeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </ElSelect>

        <ElSelect
          v-model="genreFilter"
          placeholder="题材筛选"
          style="width: 150px"
          @change="handleFilterChange"
        >
          <ElOption v-for="opt in genreOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </ElSelect>
      </div>

      <ElTable :data="scripts" v-loading="loading" border stripe>
        <ElTableColumn prop="id" label="编号" width="80" />
        <ElTableColumn prop="title" label="脚本标题（脱敏）" min-width="220">
          <template #default="{ row }">
            <span>{{ row.title }}</span>
            <ElTag v-if="row.pendingCount > 0" size="small" type="warning" class="ml-2">
              {{ row.pendingCount }} 处待核对
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="genre" label="题材" width="100" />
        <ElTableColumn prop="duration" label="时长(秒)" width="100" />
        <ElTableColumn prop="targetAudience" label="目标受众" width="120" />
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <span :class="[
              'px-2 py-1 rounded text-xs font-medium',
              row.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
              row.status === 'REVIEWING' ? 'bg-blue-100 text-blue-700' :
              row.status === 'PENDING_SUPPLEMENT' ? 'bg-purple-100 text-purple-700' :
              'bg-green-100 text-green-700'
            ]">
              {{ getStatusLabel(row.status) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="等级" width="100">
          <template #default="{ row }">
            <span v-if="row.grade" :class="[
              'px-2 py-1 rounded text-xs font-medium text-white',
              getGradeClass(row.grade)
            ]">
              {{ row.grade }}级
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="totalScore" label="总分" width="100" />
        <ElTableColumn prop="createdAt" label="创建时间" width="160" />
        <ElTableColumn label="操作" width="220">
          <template #default="{ row }">
            <ElButton size="small" @click="handleView(row.id)">查看脱敏详情</ElButton>
            <ElButton v-if="currentUser?.role !== 'VIEWER'" size="small" type="primary" @click="handleScore(row.id)">打分</ElButton>
            <ElButton v-if="isAdmin" size="small" type="danger" @click="handleDelete(row.id)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="flex justify-end mt-4">
        <ElPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <ElDialog title="确认删除" v-model="deleteDialog" width="400px">
      <p>确定要删除该脚本吗？此操作不可撤销。</p>
      <template #footer>
        <ElButton @click="deleteDialog = false">取消</ElButton>
        <ElButton type="danger" @click="confirmDelete">确定删除</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
