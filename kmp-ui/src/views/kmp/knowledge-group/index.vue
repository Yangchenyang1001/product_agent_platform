<template>
  <div class="app-container knowledge-group-page">
    <div class="page-heading">
      <div>
        <h2>知识组管理</h2>
        <p>维护知识组基础信息与默认状态</p>
      </div>
      <el-button
        type="primary"
        icon="Plus"
        @click="handleAdd"
        v-hasPermi="['kmp:knowledgeGroup:add']"
      >新增知识组</el-button>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
        <el-form-item label="知识组名称" prop="knowledgeGroupName">
          <el-input
            v-model="queryParams.knowledgeGroupName"
            placeholder="请输入知识组名称"
            clearable
            style="width: 230px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="never">
      <div class="table-toolbar">
        <span>共 {{ total }} 个知识组</span>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </div>

      <el-table v-loading="loading" :data="knowledgeGroupList">
        <el-table-column label="ID" align="center" prop="id" width="100" />
        <el-table-column label="知识组名称" prop="knowledgeGroupName" min-width="180" show-overflow-tooltip />
        <el-table-column label="知识组说明" prop="knowledgeGroupDesc" min-width="300" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.knowledgeGroupDesc || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="是否默认" align="center" prop="isDefault" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.isDefault === 1 ? 'success' : 'info'">
              {{ scope.row.isDefault === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后更新时间" align="center" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.updateTime || scope.row.createTime) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="210" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="View"
              @click="handleView(scope.row)"
              v-hasPermi="['kmp:knowledgeGroup:query']"
            >查看</el-button>
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleEdit(scope.row)"
              v-hasPermi="['kmp:knowledgeGroup:edit']"
            >编辑</el-button>
            <el-button
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['kmp:knowledgeGroup:remove']"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup name="KnowledgeGroup">
import {
  deleteKnowledgeGroup,
  listKnowledgeGroup
} from '@/api/kmp/knowledge-group'

const { proxy } = getCurrentInstance()
const router = useRouter()

const loading = ref(false)
const showSearch = ref(true)
const knowledgeGroupList = ref([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  knowledgeGroupName: undefined
})

/** 分页查询知识组列表，并仅记录必要的调试信息。 */
async function getList() {
  loading.value = true
  const params = { ...queryParams }
  console.log('[知识组管理] 查询列表，请求参数：', params)
  try {
    const response = await listKnowledgeGroup(params)
    knowledgeGroupList.value = response.rows || []
    total.value = Number(response.total) || 0
    console.log('[知识组管理] 查询列表完成：', {
      total: total.value,
      currentPageRows: knowledgeGroupList.value.length
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

/** 清空查询条件并从第一页重新查询。 */
function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.pageNum = 1
  getList()
}

/** 打开新增模式的共用维护页。 */
function handleAdd() {
  router.push({ name: 'KnowledgeGroupDetail', query: { mode: 'add' } })
}

/** 打开查看模式的共用维护页。 */
function handleView(row) {
  router.push({ name: 'KnowledgeGroupDetail', query: { mode: 'view', id: row.id } })
}

/** 打开编辑模式的共用维护页。 */
function handleEdit(row) {
  router.push({ name: 'KnowledgeGroupDetail', query: { mode: 'edit', id: row.id } })
}

/** 删除单个非默认且无业务关联的知识组。 */
async function handleDelete(row) {
  await proxy.$modal.confirm(`是否确认删除知识组“${row.knowledgeGroupName}”？`)
  console.log('[知识组管理] 删除知识组，请求 ID：', row.id)
  const response = await deleteKnowledgeGroup(row.id)
  console.log('[知识组管理] 删除知识组完成：', { id: row.id, code: response.code })
  proxy.$modal.msgSuccess('删除成功')

  // 当前页仅一条数据时回退，避免删除后停留在空白页。
  if (knowledgeGroupList.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum -= 1
  }
  getList()
}

getList()
</script>

<style scoped lang="scss">
.knowledge-group-page {
  background: var(--el-bg-color-page, #f2f3f5);
  color: var(--el-text-color-regular, #606266);
  min-height: calc(100vh - 84px);
  transition: background-color var(--el-transition-duration, 0.3s),
    color var(--el-transition-duration, 0.3s);
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
    color: var(--el-text-color-primary, #303133);
    font-size: 22px;
    font-weight: 600;
  }

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary, #909399);
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
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
}

.list-card :deep(.el-card__body) {
  padding-top: 10px;
}
</style>
