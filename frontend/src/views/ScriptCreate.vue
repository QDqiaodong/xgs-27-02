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
  description: ''
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
    ElMessage.success('脚本录入成功')
    router.push('/')
  } catch (error) {
    ElMessage.error('录入失败，请重试')
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
      <p class="text-gray-500 mt-1">录入短视频脚本基础信息</p>
    </div>
    
    <div class="bg-white rounded-xl shadow-sm p-6">
      <ElForm :model="form" label-width="120px">
        <ElFormItem label="脚本标题" required>
          <ElInput v-model="form.title" placeholder="请输入脚本标题" />
        </ElFormItem>
        
        <ElFormItem label="题材类型" required>
          <ElSelect v-model="form.genre" placeholder="请选择题材类型">
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
        
        <ElFormItem label="脚本描述">
          <ElInput v-model="form.description" type="textarea" :rows="4" placeholder="请输入脚本描述" />
        </ElFormItem>
        
        <ElFormItem>
          <ElButton type="primary" @click="handleSubmit">提交</ElButton>
          <ElButton @click="handleCancel">取消</ElButton>
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>