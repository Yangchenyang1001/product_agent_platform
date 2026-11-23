<template>
  <div class="app-container dashboard-page" v-loading="loading">
    <div class="page-heading">
      <div>
        <h2>知识看板</h2>
        <p>知识数据与查询服务运行概览</p>
      </div>
      <el-radio-group v-model="selectedRange" class="range-selector" @change="loadDashboard">
        <el-radio-button v-for="item in rangeOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-alert
      v-if="incompleteMetricNames.length"
      class="incomplete-alert"
      type="warning"
      :closable="false"
      show-icon
      title="部分源数据缺失，相关指标仅按有效数据计算"
    >
      <template #default>
        涉及指标：{{ incompleteMetricNames.join('、') }}
      </template>
    </el-alert>

    <section class="dashboard-section">
      <div class="section-heading">
        <h3>基础信息</h3>
        <span>当前数据快照，不受时间范围影响</span>
      </div>
      <div class="metric-grid basic-metric-grid">
        <article v-for="metric in basicMetricList" :key="metric.name" class="metric-card">
          <div>
            <div class="metric-name">{{ metric.name }}</div>
            <div class="metric-value-row">
              <strong>{{ metric.value }}</strong>
              <span>{{ metric.unit }}</span>
            </div>
          </div>
          <div class="metric-icon" :style="metric.iconStyle">
            <component :is="metric.icon" />
          </div>
        </article>
      </div>
    </section>

    <section class="dashboard-section">
      <div class="section-heading">
        <h3>查询信息</h3>
        <span>以下指标随统一时间范围刷新</span>
      </div>
      <div class="metric-grid query-metric-grid">
        <article v-for="metric in queryMetricList" :key="metric.name" class="metric-card">
          <div>
            <div class="metric-name">
              {{ metric.name }}
              <span class="range-tag">{{ currentRangeLabel }}</span>
            </div>
            <div class="metric-value-row">
              <strong>{{ metric.value }}</strong>
              <span>{{ metric.unit }}</span>
            </div>
          </div>
          <div class="metric-icon" :style="metric.iconStyle">
            <component :is="metric.icon" />
          </div>
        </article>
      </div>
    </section>

    <section class="dashboard-section">
      <div class="section-heading">
        <h3>Token 分析</h3>
        <span>仅直接知识查询产生 Token</span>
      </div>
      <div class="token-top-grid">
        <article class="chart-card total-token-card">
          <div class="card-title-row">
            <div>
              <h4>总 Token 消耗</h4>
              <span class="range-tag">{{ currentRangeLabel }}</span>
            </div>
            <div class="token-icon"><DataAnalysis /></div>
          </div>
          <div class="total-token-value">{{ formatNumber(overview.tokenMetrics.totalTokenCount) }}</div>
          <span class="metric-unit">Token</span>
          <div class="token-breakdown">
            <span>输入 {{ formatNumber(overview.tokenMetrics.inputTokenCount) }}</span>
            <span>输出 {{ formatNumber(overview.tokenMetrics.outputTokenCount) }}</span>
          </div>
        </article>

        <article class="chart-card token-pie-card">
          <div class="card-title-row">
            <h4>输入 Token / 输出 Token 占比</h4>
            <span class="range-tag">{{ currentRangeLabel }}</span>
          </div>
          <div ref="tokenPieRef" class="token-pie-chart" aria-label="输入和输出 Token 占比饼图" />
        </article>
      </div>

      <article class="chart-card daily-token-card">
        <div class="card-title-row">
          <h4>最近 7 天每日 Token 消耗</h4>
          <span class="range-tag">最近 7 天</span>
        </div>
        <div ref="tokenDailyRef" class="token-daily-chart" aria-label="最近七天每日 Token 消耗柱状图" />
      </article>
    </section>

    <section class="dashboard-section">
      <div class="section-heading ranking-section-heading">
        <div>
          <h3>热门排行榜</h3>
          <span>直接查询按发起时间、FAQ 按访问时间统计</span>
        </div>
      </div>
      <div class="ranking-grid">
        <article class="ranking-card">
          <div class="ranking-title">
            <div><Trophy /><h4>热门问题排行榜</h4></div>
            <span class="range-tag">{{ currentRangeLabel }}</span>
          </div>
          <div class="ranking-table">
            <div class="ranking-row ranking-header">
              <span>排名</span><span>标准问题</span><span>关联次数</span>
            </div>
            <div v-for="(item, index) in hotQuestionList" :key="item.id" class="ranking-row">
              <span><em :class="getRankingClass(index)">{{ index + 1 }}</em></span>
              <span :title="item.name">{{ item.name }}</span>
              <strong>{{ formatNumber(item.queryCount) }}</strong>
            </div>
            <el-empty v-if="!hotQuestionList.length" description="暂无热门问题" :image-size="72" />
          </div>
        </article>

        <article class="ranking-card">
          <div class="ranking-title">
            <div><CollectionTag /><h4>热门知识单元</h4></div>
            <span class="range-tag">{{ currentRangeLabel }}</span>
          </div>
          <div class="ranking-table">
            <div class="ranking-row ranking-header">
              <span>排名</span><span>知识单元名称</span><span>关联次数</span>
            </div>
            <div v-for="(item, index) in hotKnowledgeUnitList" :key="item.id" class="ranking-row">
              <span><em :class="getRankingClass(index)">{{ index + 1 }}</em></span>
              <span :title="item.name">{{ item.name }}</span>
              <strong>{{ formatNumber(item.queryCount) }}</strong>
            </div>
            <el-empty v-if="!hotKnowledgeUnitList.length" description="暂无热门知识单元" :image-size="72" />
          </div>
        </article>
      </div>
    </section>

    <footer class="dashboard-footer">知识看板数据仅供内部运营分析使用</footer>
  </div>
</template>

<script setup name="KnowledgeDashboard">
import * as echarts from 'echarts'
import {
  Aim,
  CircleCheck,
  CollectionTag,
  DataAnalysis,
  Document,
  Files,
  Search,
  Timer,
  Trophy,
  User
} from '@element-plus/icons-vue'
import {
  getDashboardOverview,
  getHotKnowledgeUnits,
  getHotQuestions
} from '@/api/kmp/dashboard'

const rangeOptions = Object.freeze([
  { label: '当日', value: 'TODAY' },
  { label: '最近 7 天', value: 'LAST_7_DAYS' },
  { label: '最近 30 天', value: 'LAST_30_DAYS' }
])
const selectedRange = ref('LAST_30_DAYS')
const loading = ref(false)
const tokenPieRef = ref(null)
const tokenDailyRef = ref(null)
const tokenPieChart = shallowRef(null)
const tokenDailyChart = shallowRef(null)
const overview = ref(createEmptyOverview())
const hotQuestionList = ref([])
const hotKnowledgeUnitList = ref([])

const currentRangeLabel = computed(() => {
  const selectedOption = rangeOptions.find(item => item.value === selectedRange.value)
  return overview.value.rangeLabel || selectedOption?.label || '最近 30 天'
})
const incompleteMetricNames = computed(() => overview.value.incompleteMetricNames)

const basicMetricList = computed(() => [
  createMetric('知识单元数量', overview.value.basicMetrics.knowledgeUnitCount, '个', Document, '#409eff'),
  createMetric('切片数量', overview.value.basicMetrics.chunkCount, '片', Files, '#67c23a')
])

const queryMetricList = computed(() => [
  createMetric('查询次数', overview.value.queryMetrics.queryCount, '次', Search, '#409eff'),
  createMetric('查询人数', overview.value.queryMetrics.visitorCount, '人', User, '#67c23a'),
  createMetric('FAQ 命中率', overview.value.queryMetrics.faqHitRate, '%', Aim, '#e6a23c', true),
  createMetric('平均 TTFT', overview.value.queryMetrics.averageTtft, 'ms', Timer, '#909399'),
  createMetric('查询成功率', overview.value.queryMetrics.querySuccessRate, '%', CircleCheck, '#36b37e', true)
])

/** 构造接口缺省时也能稳定渲染的空指标。 */
function createEmptyOverview() {
  return {
    range: 'LAST_30_DAYS',
    rangeLabel: '最近 30 天',
    basicMetrics: { knowledgeUnitCount: 0, chunkCount: 0 },
    queryMetrics: {
      queryCount: 0,
      visitorCount: 0,
      faqHitRate: null,
      averageTtft: null,
      querySuccessRate: null
    },
    tokenMetrics: { inputTokenCount: 0, outputTokenCount: 0, totalTokenCount: 0 },
    dailyTokenList: [],
    incompleteMetricNames: []
  }
}

/** 合并服务端字段，防止局部字段缺失导致整个看板无法展示。 */
function normalizeOverview(candidate) {
  const emptyOverview = createEmptyOverview()
  const source = candidate && typeof candidate === 'object' ? candidate : {}
  return {
    ...emptyOverview,
    ...source,
    basicMetrics: { ...emptyOverview.basicMetrics, ...source.basicMetrics },
    queryMetrics: { ...emptyOverview.queryMetrics, ...source.queryMetrics },
    tokenMetrics: { ...emptyOverview.tokenMetrics, ...source.tokenMetrics },
    dailyTokenList: Array.isArray(source.dailyTokenList) ? source.dailyTokenList : [],
    incompleteMetricNames: Array.isArray(source.incompleteMetricNames)
      ? source.incompleteMetricNames.filter(item => typeof item === 'string' && item.trim())
      : []
  }
}

/** 统一生成数值卡片展示对象。 */
function createMetric(name, value, unit, icon, color, decimal = false) {
  return {
    name,
    value: decimal ? formatDecimal(value) : formatNumberOrDash(value),
    unit,
    icon: markRaw(icon),
    iconStyle: { color, backgroundColor: `${color}18` }
  }
}

/** 计数统一使用整数千分位。 */
function formatNumber(value) {
  const numericValue = Number(value)
  // 非有限数属于无效源数据，计数展示规则回退为 0。
  if (!Number.isFinite(numericValue)) return '0'
  return Math.round(numericValue).toLocaleString('zh-CN')
}

/** TTFT 无有效样本时展示横线，其余按整数千分位展示。 */
function formatNumberOrDash(value) {
  // NULL 明确代表没有可用分母或样本，不能伪装成 0。
  if (value === null || value === undefined || value === '') return '--'
  return formatNumber(value)
}

/** 百分比按验收口径保留一位小数。 */
function formatDecimal(value) {
  // NULL 明确代表没有可用分母，页面必须展示横线。
  if (value === null || value === undefined || value === '') return '--'
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(1) : '--'
}

/** 查询一次概览并同步刷新所有受范围影响的指标。 */
async function loadOverview() {
  loading.value = true
  try {
    const response = await getDashboardOverview({ range: selectedRange.value })
    overview.value = normalizeOverview(response.data)
  } catch (error) {
    overview.value = normalizeOverview({
      range: selectedRange.value,
      rangeLabel: rangeOptions.find(item => item.value === selectedRange.value)?.label
    })
    console.warn('[知识看板] 概览加载失败：', { range: selectedRange.value })
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

/** 加载当前时间范围内的查询次数排行榜。 */
async function loadRankings() {
  try {
    const params = { range: selectedRange.value }
    const [hotQuestionResponse, hotKnowledgeUnitResponse] = await Promise.all([
      getHotQuestions(params),
      getHotKnowledgeUnits(params)
    ])
    hotQuestionList.value = normalizeRankingList(hotQuestionResponse.data)
    hotKnowledgeUnitList.value = normalizeRankingList(hotKnowledgeUnitResponse.data)
  } catch (error) {
    hotQuestionList.value = []
    hotKnowledgeUnitList.value = []
    console.warn('[知识看板] 热门排行榜加载失败')
  }
}

/** 时间范围变化时统一刷新概览与排行榜。 */
async function loadDashboard() {
  await Promise.all([loadOverview(), loadRankings()])
}

/** 接口异常字段不进入榜单，空结果直接展示空状态。 */
function normalizeRankingList(candidate) {
  if (!Array.isArray(candidate)) return []
  return candidate.filter(item => item && item.id != null && typeof item.name === 'string')
}

/** 初始化后集中更新两张图，避免产生重复 ECharts 实例。 */
function renderCharts() {
  renderTokenPieChart()
  renderTokenDailyChart()
}

/** 按当前范围绘制输入与输出 Token 占比。 */
function renderTokenPieChart() {
  // 首次渲染等待真实 DOM 挂载后再创建图表实例。
  if (!tokenPieChart.value && tokenPieRef.value) {
    tokenPieChart.value = echarts.init(tokenPieRef.value)
    console.log('[知识看板] 初始化图表：', { chartType: 'tokenPie', dataPointCount: 2 })
  }
  // 页面卸载或 DOM 尚未建立时无需继续配置。
  if (!tokenPieChart.value) return

  const inputTokenCount = Number(overview.value.tokenMetrics.inputTokenCount) || 0
  const outputTokenCount = Number(overview.value.tokenMetrics.outputTokenCount) || 0
  const totalTokenCount = inputTokenCount + outputTokenCount
  const inputPercent = totalTokenCount ? Math.round(inputTokenCount / totalTokenCount * 100) : 0
  tokenPieChart.value.setOption({
    color: ['#409eff', '#67c23a'],
    tooltip: { trigger: 'item', valueFormatter: value => formatNumber(value) },
    title: totalTokenCount ? {
      text: `${inputPercent}%`,
      subtext: '输入占比',
      left: '31%',
      top: '38%',
      textAlign: 'center',
      textStyle: { color: '#303133', fontSize: 22, fontWeight: 600 },
      subtextStyle: { color: '#909399', fontSize: 12 }
    } : undefined,
    legend: {
      orient: 'vertical',
      right: '8%',
      top: 'center',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 20,
      formatter(name) {
        const value = name === '输入 Token' ? inputTokenCount : outputTokenCount
        return `${name}  ${formatNumber(value)}`
      }
    },
    graphic: totalTokenCount ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: { text: '暂无 Token 数据', fill: '#909399', fontSize: 13 }
    }],
    series: [{
      name: 'Token 占比',
      type: 'pie',
      radius: ['52%', '72%'],
      center: ['32%', '52%'],
      avoidLabelOverlap: false,
      label: { show: false },
      data: totalTokenCount ? [
        { value: inputTokenCount, name: '输入 Token' },
        { value: outputTokenCount, name: '输出 Token' }
      ] : []
    }]
  }, true)
}

/** 绘制固定最近七个自然日的 Token 总消耗。 */
function renderTokenDailyChart() {
  // 首次渲染等待真实 DOM 挂载后再创建图表实例。
  if (!tokenDailyChart.value && tokenDailyRef.value) {
    tokenDailyChart.value = echarts.init(tokenDailyRef.value)
    console.log('[知识看板] 初始化图表：', {
      chartType: 'dailyTokenBar',
      dataPointCount: overview.value.dailyTokenList.length
    })
  }
  // 页面卸载或 DOM 尚未建立时无需继续配置。
  if (!tokenDailyChart.value) return

  const dailyTokenList = overview.value.dailyTokenList
  tokenDailyChart.value.setOption({
    color: ['#409eff'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: value => `${formatNumber(value)} Token`
    },
    grid: { left: 20, right: 20, top: 38, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      data: dailyTokenList.map(item => String(item.date || '').slice(5)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#ebeef5', type: 'dashed' } },
      axisLabel: {
        color: '#909399',
        formatter(value) {
          return value >= 10000 ? `${Math.round(value / 10000)}万` : formatNumber(value)
        }
      }
    },
    series: [{
      name: '每日 Token 总数',
      type: 'bar',
      barMaxWidth: 44,
      data: dailyTokenList.map(item => Number(item.totalTokenCount) || 0),
      itemStyle: { borderRadius: [3, 3, 0, 0] },
      label: {
        show: true,
        position: 'top',
        color: '#606266',
        formatter(params) {
          return params.value >= 10000 ? `${Math.round(params.value / 10000)}万` : formatNumber(params.value)
        }
      }
    }]
  }, true)
}

/** 窗口尺寸变化后自适应图表容器。 */
function resizeCharts() {
  tokenPieChart.value?.resize()
  tokenDailyChart.value?.resize()
  console.log('[知识看板] 调整图表尺寸：', { chartCount: 2 })
}

/** 前三名使用不同强调色，其余名次保持中性。 */
function getRankingClass(index) {
  return index < 3 ? `ranking-badge ranking-badge-${index + 1}` : 'ranking-badge'
}

onMounted(async () => {
  window.addEventListener('resize', resizeCharts)
  await nextTick()
  renderCharts()
  await loadDashboard()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  tokenPieChart.value?.dispose()
  tokenDailyChart.value?.dispose()
  tokenPieChart.value = null
  tokenDailyChart.value = null
})
</script>

<style scoped lang="scss">
.dashboard-page {
  min-height: calc(100vh - 84px);
  color: #303133;
  background: #f0f2f5;
}

.page-heading,
.section-heading,
.card-title-row,
.ranking-title,
.ranking-section-heading,
.ranking-title > div {
  display: flex;
  align-items: center;
}

.page-heading {
  min-height: 54px;
  justify-content: space-between;
  gap: 20px;

  h2 {
    margin: 0 0 6px;
    font-size: 20px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: #909399;
    font-size: 13px;
  }
}

.range-selector :deep(.el-radio-button__inner) {
  min-width: 92px;
}

.incomplete-alert {
  margin-top: 16px;
}

.dashboard-section {
  margin-top: 16px;
}

.section-heading {
  min-height: 28px;
  margin-bottom: 10px;
  gap: 12px;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.metric-grid {
  display: grid;
  gap: 14px;
}

.basic-metric-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.query-metric-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.metric-card,
.chart-card,
.ranking-card {
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
}

.metric-card {
  min-width: 0;
  height: 118px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-name {
  min-height: 22px;
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.range-tag {
  display: inline-block;
  margin-left: 5px;
  padding: 2px 6px;
  border-radius: 2px;
  color: #409eff !important;
  background: #ecf5ff;
  font-size: 10px !important;
  font-weight: 400;
  vertical-align: middle;
  white-space: nowrap;
}

.metric-value-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  margin-top: 7px;

  strong {
    min-width: 0;
    font-size: clamp(22px, 2vw, 27px);
    font-weight: 600;
    line-height: 1.2;
  }

  span {
    padding-bottom: 3px;
    color: #909399;
    font-size: 12px;
  }
}

.metric-icon,
.token-icon {
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: grid;
  place-items: center;

  svg {
    width: 22px;
    height: 22px;
  }
}

.token-top-grid {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 14px;
}

.chart-card {
  padding: 18px;
}

.card-title-row {
  justify-content: space-between;
  gap: 12px;

  h4 {
    display: inline-block;
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }
}

.total-token-card,
.token-pie-card {
  height: 246px;
}

.token-icon {
  color: #409eff;
  background: #ecf5ff;
}

.total-token-value {
  margin-top: 38px;
  font-size: 30px;
  font-weight: 600;
  line-height: 1.2;
}

.metric-unit {
  color: #909399;
  font-size: 12px;
}

.token-breakdown {
  margin-top: 26px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #606266;
  font-size: 12px;
}

.token-pie-chart {
  height: 188px;
}

.daily-token-card {
  height: 310px;
  margin-top: 14px;
}

.token-daily-chart {
  height: 248px;
}

.ranking-section-heading {
  justify-content: space-between;

  > div {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.ranking-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.ranking-card {
  min-width: 0;
  overflow: hidden;
}

.ranking-title {
  height: 52px;
  padding: 0 18px;
  justify-content: space-between;
  border-bottom: 1px solid #dcdfe6;

  > div {
    gap: 8px;
  }

  svg {
    width: 18px;
    color: #e6a23c;
  }

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }
}

.ranking-row {
  min-height: 54px;
  padding: 0 18px;
  display: grid;
  grid-template-columns: 62px minmax(0, 1fr) 90px;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  color: #606266;
  font-size: 13px;

  > :nth-child(2) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  > :last-child {
    text-align: right;
  }

  &:last-child {
    border-bottom: none;
  }
}

.ranking-header {
  min-height: 42px;
  color: #909399;
  background: #fafafa;
  font-size: 12px;
}

.ranking-badge {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  color: #606266;
  background: #f2f3f5;
  font-style: normal;
  font-weight: 600;
}

.ranking-badge-1 {
  color: #fff;
  background: #f56c6c;
}

.ranking-badge-2 {
  color: #fff;
  background: #e6a23c;
}

.ranking-badge-3 {
  color: #fff;
  background: #409eff;
}

.dashboard-footer {
  padding: 22px 0 4px;
  color: #a8abb2;
  text-align: center;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .query-metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .basic-metric-grid,
  .ranking-grid {
    grid-template-columns: 1fr;
  }

  .query-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .token-top-grid {
    grid-template-columns: 1fr;
  }

  .total-token-card,
  .token-pie-card {
    height: 246px;
  }
}

@media (max-width: 600px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .range-selector {
    width: 100%;

    :deep(.el-radio-button) {
      flex: 1;
    }

    :deep(.el-radio-button__inner) {
      width: 100%;
      min-width: 0;
    }
  }

  .query-metric-grid {
    grid-template-columns: 1fr;
  }

  .section-heading,
  .ranking-section-heading,
  .ranking-section-heading > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .token-pie-chart {
    height: 195px;
  }
}
</style>
