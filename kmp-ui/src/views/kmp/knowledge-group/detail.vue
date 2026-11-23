<template>
  <div class="app-container knowledge-group-detail" v-loading="loading">
    <div class="page-heading">
      <h2>知识组详情</h2>
      <div class="page-actions">
        <el-button v-if="mode === 'view'" icon="Back" @click="goBack">返回</el-button>
        <el-button
          v-if="mode === 'view'"
          type="primary"
          icon="Edit"
          @click="enterEditMode"
          v-hasPermi="['kmp:knowledgeGroup:edit']"
        >编辑</el-button>
        <el-button v-if="mode !== 'view'" icon="Close" @click="cancel">取消</el-button>
        <el-button
          v-if="mode === 'add'"
          type="primary"
          icon="Check"
          :loading="submitting"
          @click="submitForm"
          v-hasPermi="['kmp:knowledgeGroup:add']"
        >保存</el-button>
        <el-button
          v-if="mode === 'edit'"
          type="primary"
          icon="Check"
          :loading="submitting"
          @click="submitForm"
          v-hasPermi="['kmp:knowledgeGroup:edit']"
        >保存</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-title">
          <div class="card-title-main">
            <el-icon><Tickets /></el-icon>
            <span>基本信息</span>
          </div>
          <small v-if="mode !== 'view'">带 <i>*</i> 为必填项</small>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="28">
          <el-col v-if="mode !== 'add'" :xs="24" :sm="12">
            <el-form-item label="知识组 ID">
              <el-input :model-value="form.id" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="知识组名称" prop="knowledgeGroupName">
              <el-input
                v-model="form.knowledgeGroupName"
                placeholder="请输入知识组名称"
                maxlength="100"
                :readonly="mode === 'view'"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="是否默认知识组" prop="isDefault">
              <el-select v-model="form.isDefault" :disabled="mode === 'view'" style="width: 100%">
                <el-option label="是" :value="1" />
                <el-option label="否" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="知识组说明" prop="knowledgeGroupDesc">
              <el-input
                v-model="form.knowledgeGroupDesc"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                placeholder="请输入知识组说明"
                :readonly="mode === 'view'"
              />
            </el-form-item>
          </el-col>
          <template v-if="mode !== 'add'">
            <el-col :xs="24" :sm="12">
              <el-form-item label="创建人">
                <el-input :model-value="formatUser(form.createUserName, form.createUserId)" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="创建时间">
                <el-input :model-value="form.createTime || '-'" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="最后修改人">
                <el-input :model-value="formatUser(form.updateUserName, form.updateUserId)" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="最后更新时间">
                <el-input :model-value="form.updateTime || '-'" disabled />
              </el-form-item>
            </el-col>
          </template>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup name="KnowledgeGroupDetail">
import {
  addKnowledgeGroup,
  getKnowledgeGroup,
  updateKnowledgeGroup
} from '@/api/kmp/knowledge-group'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const formRef = ref()
const mode = computed(() => String(route.query.mode || ''))
const form = reactive(createEmptyForm())
const rules = {
  knowledgeGroupName: [
    { required: true, whitespace: true, message: '知识组名称不能为空', trigger: 'blur' },
    { max: 100, message: '知识组名称不能超过100个字符', trigger: 'blur' }
  ],
  knowledgeGroupDesc: [
    { max: 500, message: '知识组说明不能超过500个字符', trigger: 'blur' }
  ],
  isDefault: [
    { required: true, message: '请选择是否默认知识组', trigger: 'change' }
  ]
}

/** 创建不包含历史数据的表单，防止模式切换时审计字段串用。 */
function createEmptyForm() {
  return {
    id: undefined,
    knowledgeGroupName: '',
    knowledgeGroupDesc: '',
    isDefault: 0,
    createUserId: undefined,
    createUserName: '',
    createTime: '',
    updateUserId: undefined,
    updateUserName: '',
    updateTime: ''
  }
}

/** 校验路由模式和 ID，非法参数不向后端发起请求。 */
function validateRouteParams() {
  if (!['add', 'view', 'edit'].includes(mode.value)) {
    return false
  }
  if (mode.value === 'add') {
    return route.query.id === undefined
  }
  return /^\d+$/.test(String(route.query.id)) && Number(route.query.id) > 0
}

/** 加载查看或编辑所需详情，新增模式仅初始化默认值。 */
async function loadDetail() {
  Object.assign(form, createEmptyForm())
  formRef.value?.clearValidate()
  if (mode.value === 'add') {
    return
  }

  loading.value = true
  console.log('[知识组维护] 加载详情：', { mode: mode.value, id: route.query.id })
  try {
    const response = await getKnowledgeGroup(route.query.id)
    Object.assign(form, response.data || {})
    console.log('[知识组维护] 加载详情完成：', { id: form.id })
  } catch (error) {
    goBack()
    throw error
  } finally {
    loading.value = false
  }
}

/** 规范化用户输入，只向接口提交允许修改的四个字段。 */
function normalizeForm() {
  const description = form.knowledgeGroupDesc?.trim()
  return {
    id: mode.value === 'edit' ? form.id : undefined,
    knowledgeGroupName: form.knowledgeGroupName?.trim(),
    knowledgeGroupDesc: description || null,
    isDefault: form.isDefault
  }
}

/** 校验并提交新增或编辑请求，提交期间锁定按钮防止重复保存。 */
async function submitForm() {
  form.knowledgeGroupName = form.knowledgeGroupName?.trim()
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const submitForm = normalizeForm()
  submitting.value = true
  console.log('[知识组维护] 提交表单：', {
    mode: mode.value,
    id: submitForm.id,
    isDefault: submitForm.isDefault
  })
  try {
    if (mode.value === 'add') {
      const response = await addKnowledgeGroup(submitForm)
      console.log('[知识组维护] 新增完成：', { id: response.data, code: response.code })
      proxy.$modal.msgSuccess('新增知识组成功')
      await router.replace({
        name: 'KnowledgeGroupDetail',
        query: { mode: 'edit', id: response.data }
      })
      await loadDetail()
    } else {
      const response = await updateKnowledgeGroup(submitForm)
      console.log('[知识组维护] 编辑完成：', { id: submitForm.id, code: response.code })
      proxy.$modal.msgSuccess('修改知识组成功')
      await loadDetail()
    }
  } finally {
    submitting.value = false
  }
}

/** 由查看模式进入编辑模式并复用当前详情数据。 */
async function enterEditMode() {
  await router.replace({
    name: 'KnowledgeGroupDetail',
    query: { mode: 'edit', id: form.id }
  })
  formRef.value?.clearValidate()
}

/** 取消编辑并返回列表，不保留未提交数据。 */
function cancel() {
  goBack()
}

/** 返回知识组列表页。 */
function goBack() {
  router.push({ name: 'KnowledgeGroup' })
}

/** 按 SPEC 组合审计用户名和用户 ID。 */
function formatUser(userName, userId) {
  if (!userId) {
    return '-'
  }
  return userName ? `${userName}（用户 ID：${userId}）` : `用户 ID：${userId}`
}

onMounted(async () => {
  if (!validateRouteParams()) {
    proxy.$modal.msgError('页面参数无效')
    goBack()
    return
  }
  await loadDetail()
})
</script>

<style scoped lang="scss">
.knowledge-group-detail {
  background: var(--el-bg-color-page, #f0f2f5);
  color: var(--el-text-color-regular, #606266);
  min-height: calc(100vh - 84px);
  transition: background-color var(--el-transition-duration, 0.3s),
    color var(--el-transition-duration, 0.3s);
}

.page-heading,
.card-title,
.card-title-main {
  display: flex;
  align-items: center;
}

.page-heading {
  justify-content: space-between;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    color: var(--el-text-color-primary, #303133);
    font-size: 20px;
    font-weight: 600;
  }
}

.page-actions {
  display: flex;
  gap: 8px;
}

.card-title {
  justify-content: space-between;
  color: var(--el-text-color-primary, #303133);
  font-size: 16px;
  font-weight: 600;

  small {
    color: var(--el-text-color-secondary, #909399);
    font-size: 13px;
    font-weight: normal;
  }

  i {
    color: var(--el-color-danger, #f56c6c);
    font-style: normal;
  }
}

.card-title-main {
  gap: 8px;

  .el-icon {
    color: var(--el-color-primary, #409eff);
    font-size: 17px;
  }
}

.knowledge-group-detail :deep(.el-card) {
  border: 0;
  border-radius: 4px;

  .el-card__header {
    height: 52px;
    padding: 0 18px;
    display: flex;
    align-items: center;
  }

  .el-card__header > div {
    width: 100%;
  }

  .el-card__body {
    padding: 20px 24px 4px;
  }
}

.knowledge-group-detail :deep(.el-form-item) {
  margin-bottom: 20px;

  .el-form-item__label {
    padding-bottom: 8px;
    line-height: 20px;
  }

  .el-input__wrapper,
  .el-select__wrapper {
    min-height: 38px;
  }

  .el-textarea__inner {
    min-height: 96px !important;
    padding: 10px 12px;
  }
}

@media (max-width: 768px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }
}
</style>
