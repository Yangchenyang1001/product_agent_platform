<template>
  <div class="app-container question-cluster-detail" v-loading="loading">
    <div class="page-heading">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <div class="page-actions">
        <el-button v-if="mode === 'view'" icon="Back" @click="goBack">返回</el-button>
        <el-button
          v-if="mode === 'view'"
          type="primary"
          icon="Edit"
          @click="enterEditMode"
          v-hasPermi="['kmp:questionCluster:edit']"
        >编辑</el-button>
        <el-button
          v-if="mode !== 'add' && Number(form.status) === 1"
          type="primary"
          plain
          icon="MagicStick"
          :loading="generating"
          :disabled="generating"
          @click="handleGenerateAnswer"
          v-hasPermi="['kmp:questionCluster:generate']"
        >生成参考答案</el-button>
        <el-button
          v-if="mode === 'view' && [0, 1].includes(Number(form.status))"
          type="success"
          icon="Promotion"
          :loading="statusSubmitting"
          @click="handlePublish"
          v-hasPermi="['kmp:questionCluster:publish']"
        >发布为FAQ</el-button>
        <el-button
          v-if="mode === 'view' && Number(form.status) === 2"
          type="warning"
          icon="RefreshLeft"
          :loading="statusSubmitting"
          @click="handleUnpublish"
          v-hasPermi="['kmp:questionCluster:unpublish']"
        >取消发布</el-button>
        <el-button v-if="mode !== 'view'" icon="Close" @click="goBack">取消</el-button>
        <el-button
          v-if="mode === 'add'"
          type="primary"
          icon="Check"
          :loading="submitting"
          @click="submitForm"
          v-hasPermi="['kmp:questionCluster:add']"
        >保存</el-button>
        <el-button
          v-if="mode === 'edit'"
          type="primary"
          icon="Check"
          :loading="submitting"
          @click="submitForm"
          v-hasPermi="['kmp:questionCluster:edit']"
        >保存</el-button>
      </div>
    </div>

    <el-alert
      v-if="mode !== 'add' && isVectorSyncFailed"
      class="sync-alert"
      type="warning"
      title="问题向量同步失败，当前问题簇暂不参与自动匹配"
      :description="vectorFailureSummary"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button
          type="warning"
          link
          :loading="retryingVector"
          @click="handleRetryVector"
          v-hasPermi="['kmp:questionCluster:edit']"
        >重试同步</el-button>
      </template>
    </el-alert>

    <el-card class="base-card" shadow="never">
      <template #header>
        <div class="card-title">
          <div class="card-title-main"><el-icon><Tickets /></el-icon><span>基本信息</span></div>
          <div class="card-title-extra">
            <small v-if="mode !== 'view'">带 <i>*</i> 为必填项</small>
            <el-tag v-if="mode !== 'add'" :type="statusMeta.type">{{ statusMeta.label }}</el-tag>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="24">
          <el-col v-if="mode !== 'add'" :xs="24" :sm="12" :lg="8">
            <el-form-item label="问题簇 ID"><el-input :model-value="form.id" readonly /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="mode === 'add' ? 12 : 8">
            <el-form-item label="知识组" prop="knowledgeGroupId">
              <el-input v-if="mode === 'view'" :model-value="formatKnowledgeGroup()" readonly />
              <el-select
                v-else
                v-model="form.knowledgeGroupId"
                placeholder="请选择知识组"
                filterable
                style="width: 100%"
                @change="handleKnowledgeGroupChange"
              >
                <el-option
                  v-for="item in knowledgeGroupOptions"
                  :key="item.id"
                  :label="getKnowledgeGroupLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="mode === 'add' ? 12 : 8">
            <el-form-item label="知识单元" prop="knowledgeUnitId">
              <el-input v-if="mode === 'view'" :model-value="formatKnowledgeUnit()" readonly />
              <el-select
                v-else
                v-model="form.knowledgeUnitId"
                placeholder="请选择知识单元"
                filterable
                :disabled="!form.knowledgeGroupId"
                :loading="unitLoading"
                style="width: 100%"
              >
                <el-option
                  v-for="item in knowledgeUnitOptions"
                  :key="item.id"
                  :label="getKnowledgeUnitLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标准问题" prop="standardQuestion">
              <el-input
                v-model="form.standardQuestion"
                placeholder="请输入标准问题"
                maxlength="200"
                show-word-limit
                :readonly="mode === 'view'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标准答案" prop="standardAnswer">
              <el-input
                v-model="form.standardAnswer"
                type="textarea"
                :rows="7"
                maxlength="5000"
                show-word-limit
                placeholder="可手工填写；待发布问题簇也可生成参考答案"
                :readonly="mode === 'view'"
              />
            </el-form-item>
            <p class="answer-tip">标准答案新增时可为空，发布为 FAQ 前必填。生成的参考答案只回填页面，需手工保存。</p>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <template v-if="mode !== 'add'">
      <div class="section-heading"><h3>统计数据</h3><span>统计数据仅读</span></div>
      <el-row :gutter="14" class="statistics-row">
        <el-col v-for="item in statistics" :key="item.key" :xs="12" :sm="8" :lg="4">
          <el-card class="statistic-card" shadow="never">
            <div class="statistic-label">{{ item.label }}</div>
            <div class="statistic-value">{{ formatNumber(form[item.key]) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="recent-card" shadow="never">
        <template #header>
          <div class="card-title">
            <div class="card-title-main"><el-icon><ChatDotRound /></el-icon><span>最近关联问题</span></div>
            <small>按关联时间倒序，最多保留 10 条</small>
          </div>
        </template>
        <el-table :data="recentQuestionList" empty-text="暂无最近关联问题">
          <el-table-column label="问题 ID" prop="questionId" min-width="220" show-overflow-tooltip />
          <el-table-column label="改写后问题" prop="rewrittenQuestion" min-width="320" show-overflow-tooltip />
          <el-table-column label="提问时间" prop="questionTime" width="190">
            <template #default="scope">{{ scope.row.questionTime || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup name="QuestionClusterDetail">
import { listKnowledgeGroup } from '@/api/kmp/knowledge-group'
import { listKnowledgeUnit } from '@/api/kmp/knowledge-unit'
import {
  addQuestionCluster,
  generateReferenceAnswer,
  getQuestionCluster,
  publishQuestionCluster,
  retryQuestionClusterVector,
  unpublishQuestionCluster,
  updateQuestionCluster
} from '@/api/kmp/question-cluster'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const unitLoading = ref(false)
const generating = ref(false)
const retryingVector = ref(false)
const statusSubmitting = ref(false)
const formRef = ref()
const knowledgeGroupOptions = ref([])
const knowledgeUnitOptions = ref([])
const mode = computed(() => String(route.query.mode || ''))
const form = reactive(createEmptyForm())
let pageLoadSequence = 0
let skipNextRouteReload = false
const statusOptions = [
  { label: '沉淀中', value: 0, type: 'info' },
  { label: '待发布', value: 1, type: 'warning' },
  { label: '已发布', value: 2, type: 'success' }
]
const statistics = [
  { key: 'associationCount', label: '关联次数' },
  { key: 'faqClickCount', label: 'FAQ 点选次数' },
  { key: 'exposureCount', label: '曝光次数' },
  { key: 'helpfulCount', label: '有帮助次数' },
  { key: 'unhelpfulCount', label: '无帮助次数' },
  { key: 'refusalCount', label: '拒答次数' }
]
const rules = {
  standardQuestion: [
    { required: true, whitespace: true, message: '标准问题不能为空', trigger: 'blur' },
    { max: 200, message: '标准问题不能超过200个字符', trigger: 'blur' }
  ],
  standardAnswer: [{ max: 5000, message: '标准答案不能超过5000个字符', trigger: 'blur' }],
  knowledgeGroupId: [{ required: true, message: '请选择知识组', trigger: 'change' }],
  knowledgeUnitId: [{ required: true, message: '请选择知识单元', trigger: 'change' }]
}
const pageTitle = computed(() => mode.value === 'add' ? '新增问题簇' : mode.value === 'edit' ? '编辑问题簇' : '问题簇详情')
const pageDescription = computed(() => mode.value === 'view' ? '查看问题簇信息、FAQ 状态与最近关联问题' : '维护标准问答及其知识归属')
const statusMeta = computed(() => statusOptions.find(item => item.value === Number(form.status)) || { label: '未知', type: 'info' })
const recentQuestionList = computed(() => Array.isArray(form.recentQuestions) ? form.recentQuestions : [])
const isVectorSyncFailed = computed(() => {
  const status = form.vectorSyncStatus
  return status === false || status === 2 || ['2', 'FAILED', 'FAILURE', '失败'].includes(String(status).toUpperCase())
})
const vectorFailureSummary = computed(() => form.vectorSyncFailureReason || form.syncFailureReason || '外部向量服务暂不可用，可稍后重试。')

/** 创建空表单，避免路由模式切换时保留历史数据。 */
function createEmptyForm() {
  return {
    id: undefined,
    standardQuestion: '',
    standardAnswer: '',
    knowledgeGroupId: undefined,
    knowledgeGroupName: '',
    knowledgeUnitId: undefined,
    knowledgeUnitName: '',
    status: 0,
    associationCount: 0,
    faqClickCount: 0,
    exposureCount: 0,
    helpfulCount: 0,
    unhelpfulCount: 0,
    refusalCount: 0,
    recentQuestions: [],
    vectorSyncStatus: undefined,
    vectorSyncFailureReason: ''
  }
}

/** 校验路由模式和 ID，非法参数不向后端发起请求。 */
function validateRouteParams(pageMode, id) {
  if (!['add', 'view', 'edit'].includes(pageMode)) return false
  if (pageMode === 'add') return id === undefined
  return /^\d+$/.test(String(id)) && Number(id) > 0
}

/** 初始化页面及编辑选项。 */
async function initializePage() {
  const sequence = ++pageLoadSequence
  const pageMode = mode.value
  const id = route.query.id
  Object.assign(form, createEmptyForm())
  knowledgeGroupOptions.value = []
  knowledgeUnitOptions.value = []
  loading.value = false
  unitLoading.value = false
  formRef.value?.clearValidate()

  if (!validateRouteParams(pageMode, id)) {
    proxy.$modal.msgError('页面参数无效')
    goBack()
    return
  }
  if (pageMode === 'add') {
    await loadKnowledgeGroupOptions(sequence)
    return
  }
  await loadDetail(id, sequence)
  if (sequence !== pageLoadSequence) return
  if (pageMode === 'edit') {
    await Promise.all([
      loadKnowledgeGroupOptions(sequence),
      loadKnowledgeUnitOptions(form.knowledgeGroupId, sequence)
    ])
  }
}

/** 查询问题簇详情，不输出答案正文。 */
async function loadDetail(id = route.query.id, sequence = pageLoadSequence) {
  loading.value = true
  console.log('[问题簇详情] 加载详情：', { mode: mode.value, id })
  try {
    const response = await getQuestionCluster(id)
    if (sequence !== pageLoadSequence) return
    Object.assign(form, createEmptyForm(), response.data || {})
    form.standardAnswer ||= ''
    console.log('[问题簇详情] 加载详情完成：', { id: form.id, status: form.status })
  } catch (error) {
    if (sequence === pageLoadSequence) goBack()
    throw error
  } finally {
    if (sequence === pageLoadSequence) loading.value = false
  }
}

/** 使用既有知识组列表接口加载选项。 */
async function loadKnowledgeGroupOptions(sequence = pageLoadSequence) {
  console.log('[问题簇详情] 查询知识组选项')
  const response = await listKnowledgeGroup({ pageNum: 1, pageSize: 1000 })
  if (sequence !== pageLoadSequence) return
  knowledgeGroupOptions.value = response.rows || []
  console.log('[问题簇详情] 知识组选项查询完成：', { optionCount: knowledgeGroupOptions.value.length })
}

/** 只加载当前知识组下的知识单元。 */
async function loadKnowledgeUnitOptions(knowledgeGroupId, sequence = pageLoadSequence) {
  if (sequence !== pageLoadSequence) return
  knowledgeUnitOptions.value = []
  if (!knowledgeGroupId) return
  unitLoading.value = true
  console.log('[问题簇详情] 查询知识单元选项：', { knowledgeGroupId })
  try {
    const response = await listKnowledgeUnit({ pageNum: 1, pageSize: 1000, knowledgeGroupId })
    if (sequence === pageLoadSequence) {
      knowledgeUnitOptions.value = response.rows || []
      console.log('[问题簇详情] 知识单元选项查询完成：', { optionCount: knowledgeUnitOptions.value.length })
    }
  } finally {
    if (sequence === pageLoadSequence) unitLoading.value = false
  }
}

/** 切换知识组必须清空旧知识单元，防止提交归属不一致的数据。 */
async function handleKnowledgeGroupChange(knowledgeGroupId) {
  form.knowledgeUnitId = undefined
  await loadKnowledgeUnitOptions(knowledgeGroupId)
  formRef.value?.clearValidate('knowledgeUnitId')
}

/** 仅组装后端允许修改的四个业务字段。 */
function buildSubmitForm() {
  const answer = form.standardAnswer?.trim()
  return {
    id: mode.value === 'edit' ? form.id : undefined,
    standardQuestion: form.standardQuestion.trim(),
    standardAnswer: answer || null,
    knowledgeGroupId: form.knowledgeGroupId,
    knowledgeUnitId: form.knowledgeUnitId
  }
}

/** 校验并保存新增或编辑数据。 */
async function submitForm() {
  form.standardQuestion = form.standardQuestion.trim()
  form.standardAnswer = form.standardAnswer?.trim() || ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const submitForm = buildSubmitForm()
  submitting.value = true
  console.log('[问题簇详情] 保存表单：', {
    mode: mode.value,
    id: submitForm.id,
    knowledgeGroupId: submitForm.knowledgeGroupId,
    knowledgeUnitId: submitForm.knowledgeUnitId,
    hasStandardAnswer: Boolean(submitForm.standardAnswer)
  })
  try {
    if (mode.value === 'add') {
      const response = await addQuestionCluster(submitForm)
      const result = response.data || {}
      console.log('[问题簇详情] 新增完成：', { id: result.id, vectorSynced: result.vectorSynced, code: response.code })
      result.vectorSynced === false ? proxy.$modal.msgWarning(response.msg || '问题簇已保存，向量同步失败') : proxy.$modal.msgSuccess('新增问题簇成功')
      await replaceRouteWithoutReload({ name: 'QuestionClusterDetail', query: { mode: 'edit', id: result.id } })
      await loadDetail()
      await Promise.all([loadKnowledgeGroupOptions(), loadKnowledgeUnitOptions(form.knowledgeGroupId)])
    } else {
      const response = await updateQuestionCluster(submitForm)
      console.log('[问题簇详情] 编辑完成：', { id: submitForm.id, code: response.code })
      proxy.$modal.msgSuccess('修改问题簇成功')
      await loadDetail()
    }
  } finally {
    submitting.value = false
  }
}

/** 生成答案只覆盖当前表单，失败时恢复原内容。 */
async function handleGenerateAnswer() {
  if (generating.value) return
  const originalAnswer = form.standardAnswer
  generating.value = true
  console.log('[问题簇详情] 开始生成参考答案：', { id: form.id })
  try {
    const response = await generateReferenceAnswer(form.id)
    form.standardAnswer = response.data?.referenceAnswer || originalAnswer
    console.log('[问题簇详情] 参考答案生成完成：', { id: form.id, generated: Boolean(response.data?.referenceAnswer) })
    proxy.$modal.msgSuccess('参考答案已回填，请确认后保存')
    if (mode.value === 'view') await enterEditMode()
  } catch (error) {
    form.standardAnswer = originalAnswer
    throw error
  } finally {
    generating.value = false
  }
}

/** 重试失败的问题向量同步。 */
async function handleRetryVector() {
  retryingVector.value = true
  console.log('[问题簇详情] 重试向量同步：', { id: form.id })
  try {
    const response = await retryQuestionClusterVector(form.id)
    console.log('[问题簇详情] 向量同步重试完成：', { id: form.id, vectorSynced: response.data?.vectorSynced })
    proxy.$modal.msgSuccess('向量同步成功')
    await loadDetail()
  } finally {
    retryingVector.value = false
  }
}

/** 发布前强制校验标准答案已填写。 */
async function handlePublish() {
  if (!form.standardAnswer?.trim()) {
    proxy.$modal.msgError('请先编辑并保存标准答案')
    return
  }
  await proxy.$modal.confirm('是否确认将当前问题簇发布为 FAQ？')
  statusSubmitting.value = true
  console.log('[问题簇详情] 发布 FAQ：', { id: form.id })
  try {
    const response = await publishQuestionCluster(form.id)
    console.log('[问题簇详情] 发布 FAQ 完成：', { id: form.id, code: response.code })
    proxy.$modal.msgSuccess('发布成功')
    await loadDetail()
  } finally {
    statusSubmitting.value = false
  }
}

/** 取消已发布 FAQ 并刷新状态。 */
async function handleUnpublish() {
  await proxy.$modal.confirm('是否确认取消当前 FAQ 发布？')
  statusSubmitting.value = true
  console.log('[问题簇详情] 取消发布 FAQ：', { id: form.id })
  try {
    const response = await unpublishQuestionCluster(form.id)
    console.log('[问题簇详情] 取消发布 FAQ 完成：', { id: form.id, code: response.code })
    proxy.$modal.msgSuccess('取消发布成功')
    await loadDetail()
  } finally {
    statusSubmitting.value = false
  }
}

/** 从只读模式进入编辑模式并加载联动选项。 */
async function enterEditMode() {
  await replaceRouteWithoutReload({ name: 'QuestionClusterDetail', query: { mode: 'edit', id: form.id } })
  await Promise.all([loadKnowledgeGroupOptions(), loadKnowledgeUnitOptions(form.knowledgeGroupId)])
  formRef.value?.clearValidate()
}

async function replaceRouteWithoutReload(location) {
  skipNextRouteReload = true
  try {
    await router.replace(location)
  } catch (error) {
    skipNextRouteReload = false
    throw error
  }
}

/** 返回 quest 路由对应的问题管理列表。 */
function goBack() {
  router.push({ name: 'QuestionCluster' })
}

/** 同名知识组附加 ID，避免管理员误选。 */
function getKnowledgeGroupLabel(option) {
  const sameNameCount = knowledgeGroupOptions.value.filter(item => item.knowledgeGroupName === option.knowledgeGroupName).length
  return sameNameCount > 1 ? `${option.knowledgeGroupName}（ID：${option.id}）` : option.knowledgeGroupName
}

/** 同名知识单元附加 ID，保证知识归属选择明确。 */
function getKnowledgeUnitLabel(option) {
  const sameNameCount = knowledgeUnitOptions.value.filter(item => item.knowledgeUnitName === option.knowledgeUnitName).length
  return sameNameCount > 1 ? `${option.knowledgeUnitName}（ID：${option.id}）` : option.knowledgeUnitName
}

/** 查看模式同时展示知识组名称和数据库 ID。 */
function formatKnowledgeGroup() {
  return form.knowledgeGroupName ? `${form.knowledgeGroupName}（ID：${form.knowledgeGroupId}）` : `ID：${form.knowledgeGroupId || '-'}`
}

/** 查看模式同时展示知识单元名称和数据库 ID。 */
function formatKnowledgeUnit() {
  return form.knowledgeUnitName ? `${form.knowledgeUnitName}（ID：${form.knowledgeUnitId}）` : `ID：${form.knowledgeUnitId || '-'}`
}

/** 格式化统计数字，并区分零值和未返回值。 */
function formatNumber(value) {
  return value === null || value === undefined ? '-' : Number(value).toLocaleString('zh-CN')
}

watch(() => [mode.value, route.query.id], () => {
  if (route.name !== 'QuestionClusterDetail') return
  if (skipNextRouteReload) {
    skipNextRouteReload = false
    return
  }
  initializePage()
})

onMounted(initializePage)
</script>

<style scoped lang="scss">
.question-cluster-detail {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page, #f2f3f5);
}

.page-heading,
.card-title,
.card-title-main,
.card-title-extra,
.section-heading {
  display: flex;
  align-items: center;
}

.page-heading,
.card-title,
.section-heading {
  justify-content: space-between;
}

.page-heading {
  margin-bottom: 18px;

  h2 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 22px;
    font-weight: 600;
  }

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.sync-alert,
.base-card,
.statistics-row {
  margin-bottom: 18px;
}

.card-title-main,
.card-title-extra {
  gap: 9px;
}

.card-title-main {
  color: var(--el-text-color-primary);
  font-weight: 600;

  .el-icon {
    color: var(--el-color-primary);
  }
}

.card-title small,
.section-heading span,
.answer-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.card-title i {
  color: var(--el-color-danger);
  font-style: normal;
}

.answer-tip {
  margin: -8px 0 16px;
}

.section-heading {
  margin: 2px 0 12px;

  h3 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 18px;
  }
}

.statistic-card :deep(.el-card__body) {
  padding: 18px;
}

.statistic-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.statistic-value {
  margin-top: 10px;
  color: var(--el-text-color-primary);
  font-size: 24px;
  font-weight: 600;
}

.recent-card {
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .page-heading,
  .card-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .page-actions {
    justify-content: flex-start;
  }
}
</style>
