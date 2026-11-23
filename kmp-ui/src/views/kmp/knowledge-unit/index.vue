<template>
  <div class="app-container knowledge-unit-page">
    <div class="page-heading">
      <div>
        <h2>知识单元管理</h2>
        <p>查询和维护已成功导入的知识单元</p>
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
        <el-form-item label="所属知识组" prop="knowledgeGroupId">
          <el-select
            v-model="queryParams.knowledgeGroupId"
            placeholder="请选择知识组"
            clearable
            filterable
            style="width: 230px"
          >
            <el-option
              v-for="item in knowledgeGroupOptions"
              :key="item.id"
              :label="getKnowledgeGroupLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="never">
      <div class="table-toolbar">
        <span>共 {{ total }} 个知识单元</span>
        <right-toolbar v-model:showSearch="showSearch" :columns="columns" @queryTable="getList" />
      </div>

      <el-table v-loading="loading" :data="knowledgeUnitList">
        <el-table-column v-if="columns[0].visible" label="知识单元 ID" align="center" prop="id" width="120" />
        <el-table-column v-if="columns[1].visible" label="知识单元名称" prop="knowledgeUnitName" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="columns[2].visible" label="所属知识组" prop="knowledgeGroupName" min-width="160" show-overflow-tooltip />
        <el-table-column v-if="columns[3].visible" label="原始文件名" min-width="210" show-overflow-tooltip>
          <template #default="scope">
            <el-button
              v-if="scope.row.originalFileDownloadUrl"
              link
              type="primary"
              @click="handleDownload(scope.row)"
              v-hasPermi="['kmp:knowledgeUnit:query']"
            >{{ scope.row.originalFileName || '下载原文件' }}</el-button>
            <span v-else>{{ scope.row.originalFileName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="columns[4].visible" label="切片个数" align="center" prop="chunkCount" width="100">
          <template #default="scope">{{ formatNumber(scope.row.chunkCount) }}</template>
        </el-table-column>
        <el-table-column v-if="columns[5].visible" label="文本总长度" align="right" prop="totalTextLength" width="130">
          <template #default="scope">{{ formatNumber(scope.row.totalTextLength) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="210" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['kmp:knowledgeUnit:query']">查看</el-button>
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['kmp:knowledgeUnit:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['kmp:knowledgeUnit:remove']">删除</el-button>
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

<script setup name="KnowledgeUnit">
import { deleteKnowledgeUnit, listKnowledgeUnit } from '@/api/kmp/knowledge-unit'
import { listKnowledgeGroupOptions } from '@/api/kmp/knowledge-group'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const showSearch = ref(true)
const knowledgeUnitList = ref([])
const knowledgeGroupOptions = ref([])
const total = ref(0)
const columns = reactive([
  { key: 0, label: '知识单元 ID', visible: true },
  { key: 1, label: '知识单元名称', visible: true },
  { key: 2, label: '所属知识组', visible: true },
  { key: 3, label: '原始文件名', visible: true },
  { key: 4, label: '切片个数', visible: true },
  { key: 5, label: '文本总长度', visible: true }
])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  knowledgeUnitName: undefined,
  knowledgeGroupId: undefined
})

/** 按名称和所属知识组分页查询知识单元。 */
async function getList() {
  loading.value = true
  const params = { ...queryParams }
  console.log('[知识单元管理] 查询列表，请求参数：', params)
  try {
    const response = await listKnowledgeUnit(params)
    knowledgeUnitList.value = response.rows || []
    total.value = Number(response.total) || 0
    console.log('[知识单元管理] 查询列表完成：', {
      total: total.value,
      currentPageRows: knowledgeUnitList.value.length
    })
  } finally {
    loading.value = false
  }
}

/** 加载知识组选项，同名组同时展示 ID 便于区分。 */
async function loadKnowledgeGroupOptions() {
  console.log('[知识单元管理] 查询知识组选项')
  const response = await listKnowledgeGroupOptions()
  knowledgeGroupOptions.value = response.data || []
  console.log('[知识单元管理] 知识组选项查询完成：', { optionCount: knowledgeGroupOptions.value.length })
}

/** 从第一页按当前条件查询。 */
function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

/** 清空全部查询条件并重新查询。 */
function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.pageNum = 1
  getList()
}

/** 打开知识单元只读详情。 */
function handleView(row) {
  router.push({
    name: 'KnowledgeUnitDetail',
    params: { id: row.id },
    query: { mode: 'view' }
  })
}

/** 打开知识单元编辑详情。 */
function handleEdit(row) {
  router.push({
    name: 'KnowledgeUnitDetail',
    params: { id: row.id },
    query: { mode: 'edit' }
  })
}

/** 校验稳定下载地址后交由浏览器直接访问 MinIO。 */
function handleDownload(row) {
  const downloadUrl = String(row.originalFileDownloadUrl || '').trim()
  if (!/^https?:\/\//i.test(downloadUrl)) {
    proxy.$modal.msgError('原文件下载地址不存在')
    return
  }
  console.log('[知识单元管理] 下载原文件：', { id: row.id, fileName: row.originalFileName })
  window.open(downloadUrl, '_blank', 'noopener,noreferrer')
}

/** 二次确认后逻辑删除单个知识单元。 */
async function handleDelete(row) {
  await proxy.$modal.confirm(`是否确认删除知识单元“${row.knowledgeUnitName}”？删除后平台不再展示该记录。`)
  console.log('[知识单元管理] 删除知识单元，请求 ID：', row.id)
  const response = await deleteKnowledgeUnit(row.id)
  console.log('[知识单元管理] 删除知识单元完成：', { id: row.id, code: response.code })
  proxy.$modal.msgSuccess('删除知识单元成功')

  // 当前页只剩一条时先回退页码，避免删除后展示空白页。
  if (knowledgeUnitList.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum -= 1
  }
  getList()
}

/** 格式化统计数字，并区分“0”和未返回。 */
function formatNumber(value) {
  return value === null || value === undefined ? '-' : Number(value).toLocaleString('zh-CN')
}

/** 同名知识组附加 ID，避免管理员误选。 */
function getKnowledgeGroupLabel(option) {
  const sameNameCount = knowledgeGroupOptions.value.filter(
    item => item.knowledgeGroupName === option.knowledgeGroupName
  ).length
  return sameNameCount > 1
    ? `${option.knowledgeGroupName}（ID：${option.id}）`
    : option.knowledgeGroupName
}

onMounted(() => {
  Promise.allSettled([loadKnowledgeGroupOptions(), getList()])
})
</script>

<style scoped lang="scss">
.knowledge-unit-page {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page, #f2f3f5);
}

.page-heading,
.table-toolbar {
  display: flex;
  align-items: center;
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
</style>
