<template>
  <div class="app-container knowledge-unit-detail">
    <div class="page-heading">
      <div>
        <h2>知识单元详情</h2>
        <p>维护知识单元基础信息与切片内容</p>
      </div>
      <div class="page-actions">
        <el-button icon="Back" @click="handleBack">返回列表</el-button>
        <el-button
          v-if="editable"
          type="primary"
          icon="Check"
          :loading="baseSubmitting"
          @click="saveBaseInfo"
          v-hasPermi="['kmp:knowledgeUnit:edit']"
        >保存基础信息</el-button>
      </div>
    </div>

    <el-card v-loading="baseLoading" class="base-card" shadow="never">
      <template #header>
        <div class="card-title"><el-icon><Tickets /></el-icon><span>基础信息</span></div>
      </template>
      <el-form ref="baseFormRef" :model="baseForm" :rules="baseRules" label-position="top">
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="知识单元 ID"><el-input :model-value="baseForm.id" readonly /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="知识单元名称" prop="knowledgeUnitName">
              <el-input v-model="baseForm.knowledgeUnitName" :readonly="!editable" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="所属知识组" prop="knowledgeGroupId">
              <el-select v-model="baseForm.knowledgeGroupId" :disabled="!editable" filterable style="width: 100%">
                <el-option
                  v-for="item in knowledgeGroupOptions"
                  :key="item.id"
                  :label="getKnowledgeGroupLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="外部文档 ID（document_id）">
              <el-input :model-value="baseForm.documentId || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="原始文件">
              <div class="file-field">
                <el-input :model-value="baseForm.originalFileName || '-'" readonly />
                <el-button
                  type="primary"
                  plain
                  :disabled="!baseForm.originalFileDownloadUrl"
                  @click="downloadOriginalFile"
                  v-hasPermi="['kmp:knowledgeUnit:query']"
                >下载</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="切片个数"><el-input :model-value="formatNumber(baseForm.chunkCount)" readonly /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="文本总长度"><el-input :model-value="formatTextLength(baseForm.totalTextLength)" readonly /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24" class="audit-row">
          <el-col :xs="24" :sm="12" :lg="6" class="audit-field">
            <el-form-item label="创建人"><el-input :model-value="baseForm.createUserName || '-'" readonly /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6" class="audit-field">
            <el-form-item label="创建时间"><el-input :model-value="baseForm.createTime || '-'" readonly /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6" class="audit-field">
            <el-form-item label="最后修改人"><el-input :model-value="baseForm.updateUserName || '-'" readonly /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6" class="audit-field">
            <el-form-item label="最后更新时间"><el-input :model-value="baseForm.updateTime || '-'" readonly /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <div class="chunk-heading">
      <div class="chunk-title">
        <h3>切片内容</h3>
        <el-tag type="info" round>共 {{ chunkTotal }} 个</el-tag>
      </div>
      <el-button
        v-if="editable"
        type="primary"
        icon="Plus"
        @click="addChunkCard"
        v-hasPermi="['kmp:knowledgeUnit:add']"
      >新增切片</el-button>
    </div>

    <el-alert
      v-if="chunkError"
      class="chunk-alert"
      type="error"
      :title="chunkError"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button link type="primary" @click="loadChunks">重新加载</el-button>
      </template>
    </el-alert>

    <el-form v-loading="chunkLoading" class="chunk-list" label-position="top">
      <el-empty v-if="!chunkLoading && !chunkError && !chunkList.length" description="暂无切片内容" />
      <el-card v-for="chunk in chunkList" :key="chunk.localKey" class="chunk-card" shadow="never">
        <div class="chunk-card-heading">
          <div class="chunk-meta">
            <span>切片 ID：{{ chunk.chunkId || '保存后生成' }}</span>
            <span>{{ getContentLength(chunk) }} 字符</span>
          </div>
          <div v-if="editable" class="chunk-actions">
            <el-button
              type="primary"
              plain
              :loading="chunk.saving"
              @click="saveChunk(chunk)"
              v-hasPermi="[chunk.isNew ? 'kmp:knowledgeUnit:add' : 'kmp:knowledgeUnit:edit']"
            >保存</el-button>
            <el-button
              type="danger"
              plain
              :loading="chunk.deleting"
              @click="deleteChunk(chunk)"
              v-hasPermi="[chunk.isNew ? 'kmp:knowledgeUnit:add' : 'kmp:knowledgeUnit:remove']"
            >删除</el-button>
          </div>
        </div>
        <el-row :gutter="16" class="chunk-fields">
          <el-col class="chunk-order-column">
            <el-form-item label="顺序">
              <el-input-number
                v-model="chunk.chunkOrder"
                class="chunk-order-input"
                :min="1"
                :step="1"
                step-strictly
                :controls="false"
                :disabled="!editable"
                @change="markChunkDirty(chunk)"
              />
            </el-form-item>
          </el-col>
          <el-col class="chunk-text-column">
            <el-form-item label="标题">
              <el-input
                v-model="chunk.title"
                :readonly="!editable"
                maxlength="500"
                placeholder="请输入标题（选填）"
                @input="markChunkDirty(chunk)"
              />
            </el-form-item>
          </el-col>
          <el-col class="chunk-text-column">
            <el-form-item label="副标题">
              <el-input
                v-model="chunk.parentTitle"
                :readonly="!editable"
                maxlength="500"
                placeholder="请输入副标题（选填）"
                @input="markChunkDirty(chunk)"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="内容" class="chunk-content-field">
          <el-input
            v-model="chunk.content"
            type="textarea"
            :rows="7"
            :readonly="!editable"
            maxlength="65535"
            show-word-limit
            placeholder="请输入内容"
            @input="markChunkDirty(chunk)"
          />
        </el-form-item>
      </el-card>
    </el-form>

    <div v-if="!chunkError && (chunkTotal > 0 || chunkList.length)" class="chunk-footer">
      <span>修改切片后需单独保存，保存与删除将同步更新向量数据</span>
      <pagination
        v-show="chunkTotal > 0"
        :total="chunkTotal"
        v-model:page="chunkQuery.pageNum"
        v-model:limit="chunkQuery.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @pagination="loadChunks"
      />
    </div>
  </div>
</template>

<script setup name="KnowledgeUnitDetail">
import { onBeforeRouteLeave } from 'vue-router'
import {
  addKnowledgeChunk,
  deleteKnowledgeChunk,
  getKnowledgeUnit,
  listKnowledgeChunks,
  updateKnowledgeChunk,
  updateKnowledgeUnit
} from '@/api/kmp/knowledge-unit'
import { listKnowledgeGroupOptions } from '@/api/kmp/knowledge-group'

const MAX_CONTENT_LENGTH = 65535
const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const baseFormRef = ref()
const baseLoading = ref(false)
const baseSubmitting = ref(false)
const chunkLoading = ref(false)
const chunkError = ref('')
const knowledgeGroupOptions = ref([])
const chunkList = ref([])
const chunkTotal = ref(0)
const baseSnapshot = ref('')
const skipLeaveConfirm = ref(false)
const editable = computed(() => route.query.mode === 'edit')
const knowledgeUnitId = computed(() => String(route.params.id || ''))
const baseForm = reactive(createEmptyBaseForm())
const chunkQuery = reactive({ pageNum: 1, pageSize: 10 })
const baseRules = {
  knowledgeUnitName: [
    { required: true, whitespace: true, message: '知识单元名称不能为空', trigger: 'blur' },
    { max: 128, message: '知识单元名称不能超过128个字符', trigger: 'blur' }
  ],
  knowledgeGroupId: [{ required: true, message: '请选择所属知识组', trigger: 'change' }]
}
const hasUnsavedChanges = computed(() => {
  const baseChanged = editable.value && baseSnapshot.value && baseSnapshot.value !== getBaseSnapshot()
  return Boolean(baseChanged || chunkList.value.some(chunk => chunk.dirty))
})

/** 创建详情表单默认结构，避免切换知识单元后残留旧数据。 */
function createEmptyBaseForm() {
  return {
    id: undefined,
    documentId: '',
    knowledgeUnitName: '',
    knowledgeGroupId: undefined,
    knowledgeGroupName: '',
    originalFileName: '',
    originalFileDownloadUrl: '',
    chunkCount: undefined,
    totalTextLength: undefined,
    createUserName: '',
    createTime: '',
    updateUserName: '',
    updateTime: ''
  }
}

/** 分别加载平台基础信息和外部切片，外部故障不阻断基础信息。 */
async function initializePage() {
  if (!/^\d+$/.test(knowledgeUnitId.value) || !['view', 'edit'].includes(String(route.query.mode))) {
    proxy.$modal.msgError('页面参数无效')
    router.replace('/knowledge/knowledge-unit')
    return
  }
  await Promise.allSettled([loadKnowledgeGroupOptions(), loadDetail(), loadChunks()])
}

/** 加载知识单元基础信息。 */
async function loadDetail() {
  baseLoading.value = true
  console.log('[知识单元详情] 查询基础信息：', { knowledgeUnitId: knowledgeUnitId.value })
  try {
    const response = await getKnowledgeUnit(knowledgeUnitId.value)
    Object.assign(baseForm, createEmptyBaseForm(), response.data || {})
    baseSnapshot.value = getBaseSnapshot()
    console.log('[知识单元详情] 基础信息查询完成：', { knowledgeUnitId: baseForm.id, code: response.code })
  } finally {
    baseLoading.value = false
  }
}

/** 查询当前页切片并保留 chunkId 字符串精度。 */
async function loadChunks() {
  chunkLoading.value = true
  chunkError.value = ''
  console.log('[知识单元详情] 查询切片：', { knowledgeUnitId: knowledgeUnitId.value, ...chunkQuery })
  try {
    const response = await listKnowledgeChunks(knowledgeUnitId.value, { ...chunkQuery })
    const result = response.data || {}
    chunkTotal.value = Number(result.total) || 0
    // 使用箭头函数隔离 Array.map 的 index 参数，避免误传为 isNew。
    chunkList.value = (result.rows || []).map((row, rowIndex) => createChunkState(row, false, rowIndex))
    applyStatistics(result)
    console.log('[知识单元详情] 切片查询完成：', {
      knowledgeUnitId: knowledgeUnitId.value,
      rowCount: chunkList.value.length,
      total: chunkTotal.value,
      code: response.code
    })
    return true
  } catch (error) {
    chunkList.value = []
    chunkError.value = error.message || '切片服务暂不可用，基础信息仍可正常查看和编辑'
    console.warn('[知识单元详情] 切片查询失败：', { knowledgeUnitId: knowledgeUnitId.value })
    return false
  } finally {
    chunkLoading.value = false
  }
}

/** 加载知识组选项供基础信息编辑使用。 */
async function loadKnowledgeGroupOptions() {
  console.log('[知识单元详情] 查询知识组选项')
  const response = await listKnowledgeGroupOptions()
  knowledgeGroupOptions.value = response.data || []
  console.log('[知识单元详情] 知识组选项查询完成：', { optionCount: knowledgeGroupOptions.value.length })
}

/** 只提交允许修改的名称和知识组字段。 */
async function saveBaseInfo() {
  baseForm.knowledgeUnitName = baseForm.knowledgeUnitName.trim()
  const valid = await baseFormRef.value.validate().catch(() => false)
  if (!valid) return
  const submitForm = {
    id: baseForm.id,
    knowledgeUnitName: baseForm.knowledgeUnitName,
    knowledgeGroupId: baseForm.knowledgeGroupId
  }
  baseSubmitting.value = true
  console.log('[知识单元详情] 保存基础信息：', submitForm)
  try {
    const response = await updateKnowledgeUnit(submitForm)
    baseSnapshot.value = getBaseSnapshot()
    console.log('[知识单元详情] 基础信息保存完成：', { knowledgeUnitId: baseForm.id, code: response.code })
    proxy.$modal.msgSuccess('修改知识单元成功')
    await loadDetail()
  } finally {
    baseSubmitting.value = false
  }
}

/** 在当前页顶部创建一个仅存在于浏览器内的待保存切片。 */
function addChunkCard() {
  const chunk = createChunkState({ title: '', parentTitle: '', content: '' }, true)
  chunkList.value.unshift(chunk)
}

/** 新增或完整替换当前切片，绝不提交其他卡片的正文。 */
async function saveChunk(chunk) {
  const content = chunk.content.trim()
  if (!content) {
    proxy.$modal.msgError('切片内容不能为空')
    return
  }
  if (content.length > MAX_CONTENT_LENGTH) {
    proxy.$modal.msgError(`切片内容不能超过${MAX_CONTENT_LENGTH}个字符`)
    return
  }
  if (!chunk.isNew && !/^\d+$/.test(String(chunk.chunkId))) {
    proxy.$modal.msgError('切片 ID 格式不正确')
    return
  }
  if (!Number.isInteger(chunk.chunkOrder) || chunk.chunkOrder <= 0) {
    proxy.$modal.msgError('切片顺序必须为正整数')
    return
  }

  // 失败重试沿用同一个 requestId，防止外部服务重复新增或替换。
  chunk.requestId ||= buildRequestId()
  const submitForm = {
    // requestId: chunk.requestId,
    chunkOrder: chunk.chunkOrder,
    title: chunk.title.trim() || null,
    parentTitle: chunk.parentTitle.trim() || null,
    content
  }
  chunk.saving = true
  console.log('[知识单元详情] 保存切片：', {
    knowledgeUnitId: knowledgeUnitId.value,
    chunkId: chunk.chunkId,
    isNew: chunk.isNew,
    chunkOrder: chunk.chunkOrder,
    contentLength: content.length,
    requestId: chunk.requestId
  })
  try {
    const response = chunk.isNew
      ? await addKnowledgeChunk(knowledgeUnitId.value, submitForm)
      : await updateKnowledgeChunk(knowledgeUnitId.value, String(chunk.chunkId), submitForm)
    const result = response.data || {}
    chunk.requestId = undefined
    console.log('[知识单元详情] 切片保存完成：', {
      knowledgeUnitId: knowledgeUnitId.value,
      chunkId: result.chunkId || chunk.chunkId,
      code: response.code
    })
    proxy.$modal.msgSuccess('切片保存成功')
    if (chunk.isNew) {
      chunkQuery.pageNum = Math.max(1, Math.ceil((chunkTotal.value + 1) / chunkQuery.pageSize))
    }
    await refreshDetailAndChunks()
  } finally {
    chunk.saving = false
  }
}

/** 删除前统一二次确认；新增卡片仅移除本地数据，已保存切片立即调用后端删除。 */
async function deleteChunk(chunk) {
  const chunkLabel = chunk.isNew ? '尚未保存的新增切片' : `切片“${chunk.chunkId}”`
  await proxy.$modal.confirm(`是否确认删除${chunkLabel}？${chunk.isNew ? '' : '删除后将同步更新向量数据。'}`)

  // 新增切片尚未生成外部数据，直接从当前页面移除即可。
  if (chunk.isNew) {
    chunkList.value = chunkList.value.filter(item => item.localKey !== chunk.localKey)
    return
  }
  await removeChunk(chunk)
}

/** 删除已持久化切片，统计值只采用后端返回结果。 */
async function removeChunk(chunk) {
  if (!/^\d+$/.test(String(chunk.chunkId))) {
    proxy.$modal.msgError('切片 ID 格式不正确')
    return
  }
  chunk.deleteRequestId ||= buildRequestId()
  chunk.deleting = true
  console.log('[知识单元详情] 删除切片：', {
    knowledgeUnitId: knowledgeUnitId.value,
    chunkId: String(chunk.chunkId),
    requestId: chunk.deleteRequestId
  })
  try {
    const response = await deleteKnowledgeChunk(
      knowledgeUnitId.value,
      String(chunk.chunkId)
    )
    console.log('[知识单元详情] 切片删除完成：', {
      knowledgeUnitId: knowledgeUnitId.value,
      chunkId: String(chunk.chunkId),
      code: response.code
    })
    proxy.$modal.msgSuccess('删除切片成功')
    if (chunkList.value.filter(item => !item.isNew).length === 1 && chunkQuery.pageNum > 1) {
      chunkQuery.pageNum -= 1
    }
    await refreshDetailAndChunks()
  } finally {
    chunk.deleting = false
  }
}

/** 将接口切片转换为带独立交互状态的页面对象。 */
function createChunkState(source = {}, isNew = false, rowIndex = 0) {
  const chunkId = source.chunkId ?? source.chunk_id
  return {
    localKey: source.localKey || `${isNew ? 'new' : 'chunk'}-${chunkId || buildRequestId()}`,
    chunkId: chunkId === undefined || chunkId === null ? '' : String(chunkId),
    chunkOrder: resolveChunkOrder(source, isNew, rowIndex),
    title: source.title || '',
    parentTitle: source.parentTitle ?? source.parent_title ?? '',
    content: source.content || '',
    contentLength: Number(source.contentLength ?? source.content_length) || 0,
    isNew,
    dirty: isNew,
    saving: false,
    deleting: false,
    requestId: undefined,
    deleteRequestId: undefined
  }
}

/** 优先使用接口真实顺序；仅在旧接口缺失字段时按当前分页位置兜底。 */
function resolveChunkOrder(source, isNew, rowIndex) {
  const chunkOrder = Number(source.chunkOrder ?? source.chunk_order)
  if (Number.isInteger(chunkOrder) && chunkOrder > 0) return chunkOrder
  if (isNew) return getNextChunkOrder()
  const fallbackOrder = (chunkQuery.pageNum - 1) * chunkQuery.pageSize + rowIndex + 1
  console.warn('[知识单元详情] 切片响应缺少有效顺序，使用分页位置兜底：', {
    chunkId: source.chunkId ?? source.chunk_id,
    fallbackOrder
  })
  return fallbackOrder
}

/** 新增切片默认排在当前已知最大顺序之后。 */
function getNextChunkOrder() {
  const currentOrders = chunkList.value.map(chunk => Number(chunk.chunkOrder) || 0)
  return Math.max(chunkTotal.value, ...currentOrders, 0) + 1
}

/** 切片写操作完成后同时刷新基础信息和当前页切片。 */
async function refreshDetailAndChunks() {
  await Promise.all([loadDetail(), loadChunks()])
}

/** 标记当前卡片发生变化，后续仅保存该卡片。 */
function markChunkDirty(chunk) {
  chunk.dirty = true
  chunk.requestId = undefined
}

/** 生成符合接口幂等要求的 UUID。 */
function buildRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
    const randomValue = Math.random() * 16 | 0
    const value = character === 'x' ? randomValue : (randomValue & 0x3) | 0x8
    return value.toString(16)
  })
}

/** 使用外部服务权威统计覆盖页面，禁止前端自行加减。 */
function applyStatistics(result) {
  if (result.chunkCount !== undefined) baseForm.chunkCount = result.chunkCount
  if (result.totalTextLength !== undefined) baseForm.totalTextLength = result.totalTextLength
}

/** 返回列表时按用户明确操作直接离开，不弹出未保存提示。 */
async function handleBack() {
  skipLeaveConfirm.value = true
  try {
    await router.push('/knowledge/knowledge-unit')
  } finally {
    skipLeaveConfirm.value = false
  }
}

/** 校验下载地址后交由浏览器直接访问。 */
function downloadOriginalFile() {
  const downloadUrl = String(baseForm.originalFileDownloadUrl || '').trim()
  if (!/^https?:\/\//i.test(downloadUrl)) {
    proxy.$modal.msgError('原文件下载地址不存在')
    return
  }
  console.log('[知识单元详情] 下载原文件：', {
    knowledgeUnitId: baseForm.id,
    fileName: baseForm.originalFileName
  })
  window.open(downloadUrl, '_blank', 'noopener,noreferrer')
}

function getBaseSnapshot() {
  return JSON.stringify({
    knowledgeUnitName: baseForm.knowledgeUnitName,
    knowledgeGroupId: baseForm.knowledgeGroupId
  })
}

function getKnowledgeGroupLabel(option) {
  const sameNameCount = knowledgeGroupOptions.value.filter(
    item => item.knowledgeGroupName === option.knowledgeGroupName
  ).length
  return sameNameCount > 1 ? `${option.knowledgeGroupName}（ID：${option.id}）` : option.knowledgeGroupName
}

function getContentLength(chunk) {
  return editable.value ? chunk.content.length : chunk.contentLength
}

function formatNumber(value) {
  return value === null || value === undefined ? '-' : Number(value).toLocaleString('zh-CN')
}

function formatTextLength(value) {
  const numberText = formatNumber(value)
  return numberText === '-' ? numberText : `${numberText} 字符`
}

/** 浏览器关闭或刷新时使用原生提示保护未保存内容。 */
function handleBeforeUnload(event) {
  if (!hasUnsavedChanges.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (skipLeaveConfirm.value) return true
  if (!hasUnsavedChanges.value) return true
  try {
    await proxy.$modal.confirm('当前页面存在未保存内容，是否确认离开？')
    return true
  } catch (error) {
    return false
  }
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  initializePage()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped lang="scss">
.knowledge-unit-detail {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page, #f2f3f5);
}

.page-heading,
.chunk-heading,
.chunk-card-heading,
.chunk-meta,
.chunk-title,
.card-title,
.file-field {
  display: flex;
  align-items: center;
}

.page-heading,
.chunk-heading,
.chunk-card-heading {
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

.page-actions,
.chunk-actions {
  display: flex;
  gap: 8px;
}

.base-card {
  margin-bottom: 18px;
}

.card-title,
.chunk-title,
.chunk-meta {
  gap: 9px;
}

.card-title {
  color: var(--el-text-color-primary);
  font-weight: 600;

  .el-icon {
    color: var(--el-color-primary);
  }
}

.file-field {
  width: 100%;
  gap: 10px;

  .el-input {
    flex: 1;
  }
}

.chunk-heading {
  min-height: 42px;
  margin: 4px 0 12px;

  h3 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 18px;
  }
}

.chunk-alert,
.chunk-card {
  margin-bottom: 14px;
}

.chunk-card-heading {
  margin-bottom: 14px;
}

.chunk-meta {
  flex-wrap: wrap;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.chunk-order-input {
  width: calc(4ch + 32px);
}

.chunk-fields {
  flex-wrap: nowrap;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.chunk-order-column {
  width: auto;
  max-width: none;
  flex: 0 0 auto;
}

.chunk-text-column {
  min-width: 0;
  max-width: none;
  flex: 1 1 0;
}

.chunk-content-field {
  margin-bottom: 0;
}

.chunk-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
  font-size: 13px;

  :deep(.pagination-container) {
    margin: 0;
  }
}

@media (max-width: 768px) {
  .page-heading,
  .chunk-card-heading,
  .chunk-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .chunk-fields .el-col + .el-col {
    margin-top: 10px;
  }

  .chunk-fields {
    flex-wrap: wrap;
  }

  .chunk-order-column,
  .chunk-text-column {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
  }
}
</style>
