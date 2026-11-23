<template>
  <div class="app-container question-cluster-page">
    <div class="page-heading">
      <div>
        <h2>{{ isKnowledgeMissingMode ? '知识缺失' : '问题管理' }}</h2>
        <p>{{ isKnowledgeMissingMode ? '按无帮助与资料缺失拒答次数处理问题簇' : '维护问题簇，已发布问题簇即正式 FAQ' }}</p>
      </div>
      <div v-if="!isKnowledgeMissingMode" class="heading-actions">
        <el-button
          type="warning"
          icon="Search"
          :loading="checkSubmitting"
          :disabled="checkSubmitting"
          @click="handleCheck"
          v-hasPermi="['kmp:questionCluster:check']"
        >问题归纳</el-button>
        <el-button
          type="primary"
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['kmp:questionCluster:add']"
        >新增</el-button>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form ref="queryRef" v-show="showSearch" :model="queryParams" :inline="true">
        <el-form-item label="知识单元名称" prop="knowledgeUnitName">
          <el-input
            v-model="queryParams.knowledgeUnitName"
            placeholder="请输入知识单元名称"
            clearable
            style="width: 230px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="标准问题" prop="standardQuestion">
          <el-input
            v-model="queryParams.standardQuestion"
            placeholder="请输入标准问题"
            clearable
            style="width: 260px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="never">
      <div class="table-toolbar">
        <span>共 {{ total }} 条问题簇</span>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </div>

      <el-table v-loading="loading" :data="questionClusterList" empty-text="暂无数据">
        <el-table-column label="问题簇 ID" align="center" prop="id" width="120" />
        <el-table-column label="标准问题" prop="standardQuestion" min-width="280" show-overflow-tooltip />
        <el-table-column label="知识单元名称" prop="knowledgeUnitName" min-width="190" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="getStatusMeta(scope.row.status).type">
              {{ getStatusMeta(scope.row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isKnowledgeMissingMode"
          label="无帮助累计"
          align="center"
          prop="unhelpfulCount"
          width="120"
        />
        <el-table-column
          v-if="isKnowledgeMissingMode"
          label="拒答累计"
          align="center"
          prop="refusalCount"
          width="110"
        />
        <el-table-column label="操作" align="center" width="360" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['kmp:questionCluster:query']">查看</el-button>
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['kmp:questionCluster:edit']">编辑</el-button>
            <el-button
              v-if="[0, 1].includes(Number(scope.row.status))"
              link
              type="success"
              icon="Promotion"
              @click="handlePublish(scope.row)"
              v-hasPermi="['kmp:questionCluster:publish']"
            >发布为FAQ</el-button>
            <el-button
              v-if="Number(scope.row.status) === 2"
              link
              type="warning"
              icon="RefreshLeft"
              @click="handleUnpublish(scope.row)"
              v-hasPermi="['kmp:questionCluster:unpublish']"
            >取消发布</el-button>
            <el-button
              v-if="Number(scope.row.status) !== 2"
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['kmp:questionCluster:remove']"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup name="QuestionCluster">
import { createQuestionClusterCheckTask } from '@/api/kmp/question-cluster-check'
import {
  deleteQuestionCluster,
  listKnowledgeMissing,
  listQuestionCluster,
  publishQuestionCluster,
  unpublishQuestionCluster
} from '@/api/kmp/question-cluster'

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const showSearch = ref(true)
const questionClusterList = ref([])
const total = ref(0)
const checkSubmitting = ref(false)
const statusOptions = [
  { label: '沉淀中', value: 0, type: 'info' },
  { label: '待发布', value: 1, type: 'warning' },
  { label: '已发布', value: 2, type: 'success' }
]
const isKnowledgeMissingMode = computed(() => route.path.endsWith('/knowledge-missing'))
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  knowledgeUnitName: undefined,
  standardQuestion: undefined,
  status: undefined
})

/** 按当前页面模式、知识单元、标准问题和状态分页查询。 */
async function getList() {
  loading.value = true
  const params = { ...queryParams }
  const pageName = isKnowledgeMissingMode.value ? '知识缺失' : '问题管理'
  console.log(`[${pageName}] 查询列表，请求参数：`, params)
  try {
    const listApi = isKnowledgeMissingMode.value ? listKnowledgeMissing : listQuestionCluster
    const response = await listApi(params)
    questionClusterList.value = response.rows || []
    total.value = Number(response.total) || 0
    console.log(`[${pageName}] 查询列表完成：`, {
      total: total.value,
      currentPageRows: questionClusterList.value.length
    })
  } finally {
    loading.value = false
  }
}

/** 从第一页按当前条件查询。 */
function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

/** 清空查询条件并重新查询。 */
function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.pageNum = 1
  getList()
}

/** 打开新增模式的问题详情页。 */
function handleAdd() {
  router.push({ name: 'QuestionClusterDetail', query: { mode: 'add' } })
}

/** 确认后提交待检测问题归纳任务，执行过程通过后端中文日志观察。 */
async function handleCheck() {
  await proxy.$modal.confirm('将归纳当前仍处于待检测状态的问题，是否继续？')
  checkSubmitting.value = true
  console.log('[问题归纳] 创建手动归纳任务')
  try {
    const response = await createQuestionClusterCheckTask()
    const taskId = response.data?.taskId
    console.log('[问题归纳] 手动归纳任务创建完成：', { taskId })
    proxy.$modal.msgSuccess(`问题归纳任务已提交，任务 ID：${taskId}，执行过程请查看后台日志`)
  } finally {
    checkSubmitting.value = false
  }
}

/** 打开只读详情。 */
function handleView(row) {
  router.push({ name: 'QuestionClusterDetail', query: { mode: 'view', id: row.id } })
}

/** 打开编辑详情。 */
function handleEdit(row) {
  router.push({ name: 'QuestionClusterDetail', query: { mode: 'edit', id: row.id } })
}

/** 二次确认后删除未发布问题簇。 */
async function handleDelete(row) {
  await proxy.$modal.confirm(`是否确认删除问题簇“${row.standardQuestion}”？删除后将不再参与匹配。`)
  console.log('[问题管理] 删除问题簇：', { id: row.id })
  const response = await deleteQuestionCluster(row.id)
  console.log('[问题管理] 删除问题簇完成：', { id: row.id, code: response.code })
  proxy.$modal.msgSuccess('删除成功')

  // 当前页仅剩一条时先回退，避免删除后停留在空白页。
  if (questionClusterList.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum -= 1
  }
  getList()
}

/** 确认答案已准备后发布为 FAQ。 */
async function handlePublish(row) {
  await proxy.$modal.confirm(`是否确认将问题簇“${row.standardQuestion}”发布为 FAQ？`)
  console.log('[问题管理] 发布 FAQ：', { id: row.id })
  const response = await publishQuestionCluster(row.id)
  console.log('[问题管理] 发布 FAQ 完成：', { id: row.id, code: response.code })
  proxy.$modal.msgSuccess('发布成功')
  getList()
}

/** 二次确认后取消 FAQ 发布。 */
async function handleUnpublish(row) {
  await proxy.$modal.confirm(`是否确认取消问题簇“${row.standardQuestion}”的 FAQ 发布？`)
  console.log('[问题管理] 取消发布 FAQ：', { id: row.id })
  const response = await unpublishQuestionCluster(row.id)
  console.log('[问题管理] 取消发布 FAQ 完成：', { id: row.id, code: response.code })
  proxy.$modal.msgSuccess('取消发布成功')
  getList()
}

/** 返回状态的统一文案和标签样式。 */
function getStatusMeta(status) {
  return statusOptions.find(item => item.value === Number(status)) || { label: '未知', type: 'info' }
}

// 同一组件被两个菜单复用，路由模式变化时必须重新从第一页请求对应接口。
watch(() => route.path, () => {
  queryParams.pageNum = 1
  getList()
}, { immediate: true })
</script>

<style scoped lang="scss">
.question-cluster-page {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page, #f2f3f5);
}

.page-heading,
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.heading-actions {
  display: flex;
  align-items: center;
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

.search-card {
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.table-toolbar {
  min-height: 38px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.list-card :deep(.el-card__body) {
  padding-top: 10px;
}

@media (max-width: 768px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }
}
</style>
