<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElCard } from 'element-plus'
import * as echarts from 'echarts'
import { getGradeDistribution, getStatusStatistics } from '@/api/statistics'
import type { GradeDistributionResponse } from '@/types'

const gradeDistribution = ref<GradeDistributionResponse[]>([])
const statusStats = ref<Record<string, number>>({})
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null

const gradeColors = {
  S: '#ef4444',
  A: '#f97316',
  B: '#3b82f6',
  C: '#6b7280'
}

const loadStatistics = async () => {
  try {
    gradeDistribution.value = await getGradeDistribution()
    statusStats.value = await getStatusStatistics()
    initCharts()
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const initCharts = () => {
  const pieDom = document.getElementById('pieChart')
  const barDom = document.getElementById('barChart')
  
  if (pieDom) {
    pieChart = echarts.init(pieDom)
    pieChart.setOption({
      title: {
        text: '稿件等级分布',
        left: 'center'
      },
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        data: ['S级', 'A级', 'B级', 'C级']
      },
      series: [
        {
          name: '等级分布',
          type: 'pie',
          radius: '55%',
          center: ['50%', '50%'],
          data: gradeDistribution.value.map(item => ({
            value: item.count,
            name: item.grade + '级',
            itemStyle: {
              color: gradeColors[item.grade as keyof typeof gradeColors]
            }
          })),
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }
      ]
    })
  }
  
  if (barDom) {
    barChart = echarts.init(barDom)
    barChart.setOption({
      title: {
        text: '稿件状态分布',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: ['待评审', '评审中', '待补评', '已分级']
      },
      yAxis: {
        type: 'value'
      },
      series: [
        {
          name: '数量',
          type: 'bar',
          data: [
            statusStats.value.pending || 0,
            statusStats.value.reviewing || 0,
            statusStats.value.pendingSupplement || 0,
            statusStats.value.graded || 0
          ],
          itemStyle: {
            color: ['#eab308', '#3b82f6', '#a855f7', '#22c55e']
          }
        }
      ]
    })
  }
}

const handleResize = () => {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  loadStatistics()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-gray-800">统计分析</h2>
      <p class="text-gray-500 mt-1">稿件等级分布数据可视化</p>
    </div>
    
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <ElCard class="text-center">
        <div class="text-3xl font-bold text-red-500">{{ gradeDistribution.find(g => g.grade === 'S')?.count || 0 }}</div>
        <div class="text-gray-500 mt-1">S级稿件</div>
      </ElCard>
      <ElCard class="text-center">
        <div class="text-3xl font-bold text-orange-500">{{ gradeDistribution.find(g => g.grade === 'A')?.count || 0 }}</div>
        <div class="text-gray-500 mt-1">A级稿件</div>
      </ElCard>
      <ElCard class="text-center">
        <div class="text-3xl font-bold text-blue-500">{{ gradeDistribution.find(g => g.grade === 'B')?.count || 0 }}</div>
        <div class="text-gray-500 mt-1">B级稿件</div>
      </ElCard>
      <ElCard class="text-center">
        <div class="text-3xl font-bold text-gray-500">{{ gradeDistribution.find(g => g.grade === 'C')?.count || 0 }}</div>
        <div class="text-gray-500 mt-1">C级稿件</div>
      </ElCard>
    </div>
    
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <ElCard shadow="never">
        <div id="pieChart" class="w-full h-80"></div>
      </ElCard>
      <ElCard shadow="never">
        <div id="barChart" class="w-full h-80"></div>
      </ElCard>
    </div>
    
    <ElCard title="等级占比详情" shadow="never">
      <table class="w-full">
        <thead>
          <tr class="border-b">
            <th class="text-left py-2">等级</th>
            <th class="text-right py-2">数量</th>
            <th class="text-right py-2">占比</th>
            <th class="text-center py-2">进度条</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in gradeDistribution" :key="item.grade" class="border-b last:border-0">
            <td class="py-3">
              <span :class="[
                'px-2 py-1 rounded text-xs font-medium text-white',
                gradeColors[item.grade as keyof typeof gradeColors]
              ]">
                {{ item.grade }}级
              </span>
            </td>
            <td class="text-right py-3">{{ item.count }}</td>
            <td class="text-right py-3">{{ item.percentage }}%</td>
            <td class="py-3">
              <div class="w-full bg-gray-200 rounded-full h-2">
                <div
                  :class="[
                    'h-2 rounded-full transition-all',
                    gradeColors[item.grade as keyof typeof gradeColors]
                  ]"
                  :style="{ width: item.percentage + '%' }"
                ></div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </ElCard>
  </div>
</template>