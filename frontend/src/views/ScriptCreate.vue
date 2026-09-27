<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createScript } from '@/api/script'

const router = useRouter()
const form = ref({
  title: '',
  genre: '',
  duration: 0,
  targetAudience: '',
  author: '',
  source: '',
  contactInfo: '',
  description: '',
  attachmentNote: ''
})

const genres = [
  '搞笑', '情感', '科普', '生活', '美食', '旅游', '科技', '娱乐', '体育', '教育'
]

const audiences = [
  '青少年', '青年', '中年', '老年', '女性', '男性', '职场人士', '学生'
]

const handleSubmit = async () => {
  if (!form.value.title || !form.value.genre || !form.value.duration || !form.value.targetAudience) {
    ElMessage.warning('请填写所有必填项')
    return
  }

  try {
    await createScript(form.value)
    ElMessage.success('脚本录入成功，系统已自动生成双盲脱敏视图')
    router.push('/')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || '录入失败，请重试')
  }
}

const handleCancel = () => {
  router.push('/')
}
</script>

<template>
  <div class="max-w-2xl mx-auto">
    <div class="mb-6">
      <h2 class="text-2xl font-bold text-gray-800">脚本录入</h2>
      <p class="text-gray-500 mt-1">
        录入短视频脚本原稿。作者、来源机构、联系方式与正文中的身份线索将由系统自动识别并生成稳定代号，
        评委与查看员只能看到脱敏视图。
      </p>
    </div>

    <div class="bg-white rounded-xl shadow-sm p-6">
      <ElForm :model="form" label-width="120px">
        <ElFormItem label="脚本标题" required>
          <ElInput v-model="form.title" placeholder="请输入脚本标题（标题中的投稿人姓名也会被识别脱敏）" />
        </ElFormItem>

        <ElFormItem label="题材类型" required>
          <ElSelect v-model="form.genre" placeholder="选择题材类型">
            <ElOption v-for="genre in genres" :key="genre" :label="genre" :value="genre" />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="时长(秒)" required>
          <ElInput v-model.number="form.duration" type="number" placeholder="请输入时长" />
        </ElFormItem>

        <ElFormItem label="目标受众" required>
          <ElSelect v-model="form.targetAudience" placeholder="请选择目标受众">
            <ElOption v-for="audience in audiences" :key="audience" :label="audience" :value="audience" />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="作者姓名">
          <ElInput v-model="form.author" placeholder="投稿人/编剧真实姓名（原稿仅管理员受控可见）" />
        </ElFormItem>

        <ElFormItem label="来源机构">
          <ElInput v-model="form.source" placeholder="如：成都微光文化传媒有限公司；内部投稿可直接填“内部投稿”" />
        </ElFormItem>

        <ElFormItem label="联系方式">
          <ElInput
            v-model="form.contactInfo"
            type="textarea"
            :rows="3"
            placeholder="电话、邮箱、微信/抖音等社交账号（整体识别、整体替换，不会只遮住一半）"
          />
        </ElFormItem>

        <ElFormItem label="脚本描述">
          <ElInput v-model="form.description" type="textarea" :rows="6" placeholder="请输入脚本正文/描述" />
        </ElFormItem>

        <ElFormItem label="附件说明">
          <ElInput
            v-model="form.attachmentNote"
            type="textarea"
            :rows="3"
            placeholder="附件文件名、授权说明、素材链接等（与正文遵守同一套脱敏规则）"
          />
        </ElFormItem>

        <ElFormItem>
          <ElButton type="primary" @click="handleSubmit">提交</ElButton>
          <ElButton @click="handleCancel">取消</ElButton>
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>
