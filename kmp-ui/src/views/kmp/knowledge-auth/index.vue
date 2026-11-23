<template>
  <div class="app-container knowledge-auth-page">
    <el-card class="list-card" shadow="never">
      <div class="list-heading">
        <h2>知识权限</h2>
        <p>按知识组配置智能问答数据访问范围</p>
      </div>

      <el-form ref="queryRef" :model="queryParams" :inline="true">
        <el-form-item label="知识组名称" prop="knowledgeGroupName">
          <el-input
            v-model="queryParams.knowledgeGroupName"
            placeholder="请输入知识组名称"
            maxlength="100"
            clearable
            style="width: 230px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="permissionList">
        <el-table-column label="知识组 ID" align="center" prop="knowledgeGroupId" min-width="80" />
        <el-table-column label="知识组名称" prop="knowledgeGroupName" min-width="190" show-overflow-tooltip />
        <el-table-column label="知识单元个数" align="center" prop="knowledgeUnitCount" min-width="110" />
        <el-table-column label="FAQ 个数" align="center" prop="faqCount" min-width="90" />
        <el-table-column label="全局授权" align="center" prop="isGlobal" min-width="100">
          <template #default="scope">
            <el-tag :type="Number(scope.row.isGlobal) === 1 ? 'success' : 'info'">
              {{ Number(scope.row.isGlobal) === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联部门个数" align="center" prop="departmentCount" min-width="100" />
        <el-table-column label="关联角色个数" align="center" prop="roleCount" min-width="100" />
        <el-table-column label="关联人员个数" align="center" prop="userCount" min-width="100" />
        <el-table-column label="操作" align="center" min-width="130">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Key"
              @click="handlePermission(scope.row)"
              v-hasPermi="['kmp:knowledgeAuth:query']"
            >权限管理</el-button>
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

    <el-dialog
      v-model="dialogOpen"
      title="权限分配"
      width="820px"
      class="knowledge-auth-dialog"
      append-to-body
      :close-on-click-modal="false"
      @closed="closeDialog"
    >
      <div v-loading="dialogLoading" class="permission-dialog-body">
        <div class="group-summary">
          <strong>知识组：{{ submitForm.knowledgeGroupName }}</strong>
          <span>知识组 ID：{{ submitForm.knowledgeGroupId }}</span>
        </div>

        <div class="global-permission">
          <el-checkbox
            v-model="globalChecked"
            :disabled="dialogLoading"
            @change="handleGlobalChange"
          >全局权限</el-checkbox>
          <span>所有已登录且具有“知识查询”权限的用户均可访问</span>
          <div class="global-tip">勾选后无需再选择其他权限，下方部门、角色、个人配置将停用。</div>
        </div>

        <el-tabs v-model="activeTab" :class="{ 'permission-tabs-disabled': globalChecked }">
          <el-tab-pane label="部门权限" name="department">
            <div
              v-loading="departmentLoading"
              class="department-tree-select"
              :class="{ 'department-tree-select-disabled': globalChecked }"
            >
              <div class="department-tree-header">部门名称</div>
              <el-tree
                ref="departmentTreeRef"
                :data="departmentOptions"
                :props="departmentTreeProps"
                node-key="deptId"
                show-checkbox
                default-expand-all
                :expand-on-click-node="false"
                @check="handleDepartmentCheck"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="角色权限" name="role">
            <el-table
              ref="roleTableRef"
              v-loading="roleLoading"
              :data="roleOptions"
              height="306"
              row-key="roleId"
              @selection-change="handleRoleSelectionChange"
            >
              <el-table-column type="selection" width="55" :selectable="isOptionSelectable" />
              <el-table-column label="角色 ID" align="center" prop="roleId" width="160" />
              <el-table-column label="角色名称" prop="roleName" min-width="565" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="个人权限" name="user">
            <div class="tab-toolbar">
              <el-form :inline="true" :model="userQuery">
                <el-form-item label="用户姓名">
                  <el-input
                    v-model="userQuery.nickName"
                    placeholder="请输入用户姓名"
                    clearable
                    style="width: 200px"
                    :disabled="globalChecked"
                    @keyup.enter="handleUserQuery"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :disabled="globalChecked" @click="handleUserQuery">查询</el-button>
                  <el-button :disabled="globalChecked" @click="resetUserQuery">重置</el-button>
                </el-form-item>
              </el-form>
              <span class="selected-count">已选 {{ submitForm.userIds.length }} 人</span>
            </div>
            <el-table
              ref="userTableRef"
              v-loading="userLoading"
              :data="userOptions"
              height="306"
              row-key="userId"
              @selection-change="handleUserSelectionChange"
            >
              <el-table-column type="selection" width="55" :selectable="isOptionSelectable" />
              <el-table-column label="用户 ID" align="center" prop="userId" width="100" />
              <el-table-column label="用户姓名" prop="nickName" width="220" show-overflow-tooltip />
              <el-table-column label="所属部门" prop="dept.deptName" min-width="405" show-overflow-tooltip>
                <template #default="scope">{{ scope.row.dept?.deptName || '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="other-tab-note">
              <span>角色权限：角色列表多选</span>
              <span>个人权限：支持姓名模糊查询与人员多选</span>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitLoading"
          :disabled="dialogLoading"
          @click="submitPermission"
          v-hasPermi="['kmp:knowledgeAuth:edit']"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="KnowledgeAuth">
import { nextTick } from 'vue'
import { listDept } from '@/api/system/dept'
import { listRole } from '@/api/system/role'
import { listUser } from '@/api/system/user'
import {
  getKnowledgeAuth,
  listKnowledgeAuth,
  saveKnowledgeAuth
} from '@/api/kmp/knowledge-auth'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const total = ref(0)
const permissionList = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  knowledgeGroupName: undefined
})

const dialogOpen = ref(false)
const dialogLoading = ref(false)
const submitLoading = ref(false)
const activeTab = ref('department')
const globalChecked = ref(false)
const submitForm = reactive(createEmptySubmitForm())

const departmentTreeRef = ref()
const roleTableRef = ref()
const userTableRef = ref()
const departmentLoading = ref(false)
const roleLoading = ref(false)
const userLoading = ref(false)
const departmentOptions = ref([])
const roleOptions = ref([])
const userOptions = ref([])
const selectedUserMap = new Map()
const syncingSelection = ref(false)

const departmentTreeProps = { children: 'children', label: 'deptName' }
const userQuery = reactive({ pageNum: 1, pageSize: 1000, nickName: undefined })

/** 创建弹窗表单的统一初始值，避免关闭后遗留上一个知识组的数据。 */
function createEmptySubmitForm() {
  return {
    knowledgeGroupId: undefined,
    knowledgeGroupName: '',
    isGlobal: 0,
    departmentIds: [],
    roleIds: [],
    userIds: []
  }
}

/** 分页查询知识权限概览。 */
async function getList() {
  loading.value = true
  const params = {
    ...queryParams,
    knowledgeGroupName: queryParams.knowledgeGroupName?.trim() || undefined
  }
  console.log('[权限管理] 查询列表，请求参数：', params)
  try {
    const response = await listKnowledgeAuth(params)
    permissionList.value = response.rows || []
    total.value = Number(response.total) || 0
    console.log('[权限管理] 查询列表完成：', {
      code: response.code,
      pageNum: queryParams.pageNum,
      rowCount: permissionList.value.length
    })
  } finally {
    loading.value = false
  }
}

/** 从第一页按知识组名称查询。 */
function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

/** 清空列表查询条件。 */
function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.pageNum = 1
  getList()
}

/** 打开权限分配弹窗，并行加载现有配置及三类授权对象。 */
async function handlePermission(row) {
  resetDialogState()
  dialogOpen.value = true
  dialogLoading.value = true
  console.log('[权限管理] 打开权限分配，请求知识组 ID：', row.knowledgeGroupId)
  try {
    const [detailResponse] = await Promise.all([
      getKnowledgeAuth(row.knowledgeGroupId),
      loadDepartments(),
      loadRoles(),
      loadUsers()
    ])
    const detail = detailResponse.data || {}
    Object.assign(submitForm, {
      knowledgeGroupId: detail.knowledgeGroupId ?? row.knowledgeGroupId,
      knowledgeGroupName: detail.knowledgeGroupName || row.knowledgeGroupName,
      isGlobal: Number(detail.isGlobal) === 1 ? 1 : 0,
      departmentIds: normalizeIds(detail.departmentIds),
      roleIds: normalizeIds(detail.roleIds),
      userIds: normalizeIds(detail.userIds)
    })
    globalChecked.value = submitForm.isGlobal === 1
    // 先登记全部已选用户 ID，确保姓名筛选之外的用户也不会丢失。
    submitForm.userIds.forEach(userId => selectedUserMap.set(String(userId), null))
    await restoreAllSelections()
    console.log('[权限管理] 权限详情加载完成：', {
      code: detailResponse.code,
      knowledgeGroupId: submitForm.knowledgeGroupId,
      isGlobal: submitForm.isGlobal,
      departmentCount: submitForm.departmentIds.length,
      roleCount: submitForm.roleIds.length,
      userCount: submitForm.userIds.length
    })
  } catch (error) {
    console.log('[权限管理] 打开权限分配失败：', {
      knowledgeGroupId: row.knowledgeGroupId,
      message: error?.message
    })
    dialogOpen.value = false
  } finally {
    dialogLoading.value = false
  }
}

/** 勾选全局权限前二次确认，并在确认后清空互斥的三类授权。 */
async function handleGlobalChange(checked) {
  // 取消全局权限无需确认，后续由管理员重新选择授权对象。
  if (!checked) {
    submitForm.isGlobal = 0
    return
  }
  try {
    await proxy.$modal.confirm('启用全局权限将清空已选择的部门、角色和个人权限，是否继续？')
    submitForm.isGlobal = 1
    submitForm.departmentIds = []
    submitForm.roleIds = []
    submitForm.userIds = []
    selectedUserMap.clear()
    await restoreAllSelections()
  } catch {
    globalChecked.value = false
    submitForm.isGlobal = 0
  }
}

/** 加载正常部门并组装原型要求的部门树。 */
async function loadDepartments() {
  departmentLoading.value = true
  const params = { status: '0' }
  console.log('[权限管理] 查询部门选项，请求参数：', params)
  try {
    const response = await listDept(params)
    departmentOptions.value = proxy.handleTree(response.data || [], 'deptId')
    console.log('[权限管理] 查询部门选项完成：', {
      code: response.code,
      optionCount: flattenTree(departmentOptions.value).length
    })
    await restoreDepartmentSelection()
  } finally {
    departmentLoading.value = false
  }
}

/** 加载正常角色；原型使用无查询区的精简多选列表。 */
async function loadRoles() {
  roleLoading.value = true
  const params = {
    pageNum: 1,
    pageSize: 1000,
    status: '0'
  }
  console.log('[权限管理] 查询角色选项，请求参数：', params)
  try {
    const response = await listRole(params)
    // 切换数据源期间屏蔽表格清空事件，避免查询后误删已有选择。
    syncingSelection.value = true
    roleOptions.value = response.rows || []
    console.log('[权限管理] 查询角色选项完成：', {
      code: response.code,
      optionCount: roleOptions.value.length
    })
    await restoreRoleSelection()
  } finally {
    roleLoading.value = false
  }
}

/** 加载正常用户，并通过 ID 集合保留姓名筛选之外的勾选状态。 */
async function loadUsers() {
  userLoading.value = true
  const nickName = userQuery.nickName?.trim() || ''
  const params = { ...userQuery, nickName: nickName || undefined, status: '0' }
  console.log('[权限管理] 查询人员选项，请求参数：', params)
  try {
    const response = await listUser(params)
    // 切换筛选结果期间屏蔽表格清空事件，完整选择只由业务集合维护。
    syncingSelection.value = true
    const responseUsers = response.rows || []
    // 兼容尚未重启、仍未加载 nickName SQL 条件的后端实例，前端按姓名再次过滤响应。
    userOptions.value = nickName
      ? responseUsers.filter(user => String(user.nickName || '').toLocaleLowerCase().includes(nickName.toLocaleLowerCase()))
      : responseUsers

    // 已选用户对象只用于筛选回显；提交仍只发送用户 ID，避免携带敏感字段。
    userOptions.value.forEach(user => {
      // 仅补全详情接口返回的已选用户，未选择用户不进入跨页集合。
      if (submitForm.userIds.some(userId => sameId(userId, user.userId))) {
        selectedUserMap.set(String(user.userId), user)
      }
    })
    console.log('[权限管理] 查询人员选项完成：', {
      code: response.code,
      pageNum: userQuery.pageNum,
      responseCount: responseUsers.length,
      optionCount: userOptions.value.length
    })
    await restoreUserSelection()
  } finally {
    userLoading.value = false
  }
}

/** 按树控件完整勾选节点保存部门 ID；半选父节点不作为直接授权对象。 */
function handleDepartmentCheck() {
  submitForm.departmentIds = normalizeIds(departmentTreeRef.value?.getCheckedKeys(false) || [])
}

/** 更新当前角色结果内的选择，同时保留筛选结果外的已选 ID。 */
function handleRoleSelectionChange(rows) {
  // 程序恢复勾选时不反向修改业务选择集合。
  if (syncingSelection.value) return
  const visibleIds = roleOptions.value.map(item => item.roleId)
  submitForm.roleIds = mergePageSelection(submitForm.roleIds, visibleIds, rows.map(item => item.roleId))
}

/** 更新当前筛选结果的选择，通过 Map 保留筛选之外的已选用户。 */
function handleUserSelectionChange(rows) {
  // 程序恢复勾选时不反向修改跨页选择集合。
  if (syncingSelection.value) return

  // 只移除当前筛选结果中被取消的用户，筛选之外的选中状态必须保持。
  userOptions.value.forEach(user => selectedUserMap.delete(String(user.userId)))
  // 当前页仍勾选的用户重新写入集合，以当前页面对象更新回显信息。
  rows.forEach(user => selectedUserMap.set(String(user.userId), user))
  submitForm.userIds = Array.from(selectedUserMap.keys()).map(toSafeId)
}

/** 保存完整权限快照；空配置必须再次确认业务影响。 */
async function submitPermission() {
  const emptyPermission = !globalChecked.value
    && submitForm.departmentIds.length === 0
    && submitForm.roleIds.length === 0
    && submitForm.userIds.length === 0
  // 空授权会使知识组无人可访问，必须让管理员明确确认业务影响。
  if (emptyPermission) {
    await proxy.$modal.confirm('保存后该知识组将无人可访问，是否继续？')
  }

  const requestBody = {
    isGlobal: globalChecked.value ? 1 : 0,
    departmentIds: globalChecked.value ? [] : normalizeIds(submitForm.departmentIds),
    roleIds: globalChecked.value ? [] : normalizeIds(submitForm.roleIds),
    userIds: globalChecked.value ? [] : normalizeIds(submitForm.userIds)
  }
  submitLoading.value = true
  console.log('[权限管理] 保存权限，请求摘要：', {
    knowledgeGroupId: submitForm.knowledgeGroupId,
    isGlobal: requestBody.isGlobal,
    departmentCount: requestBody.departmentIds.length,
    roleCount: requestBody.roleIds.length,
    userCount: requestBody.userIds.length
  })
  try {
    const response = await saveKnowledgeAuth(submitForm.knowledgeGroupId, requestBody)
    console.log('[权限管理] 保存权限完成：', {
      code: response.code,
      knowledgeGroupId: submitForm.knowledgeGroupId
    })
    proxy.$modal.msgSuccess(response.msg || '保存知识权限成功')
    dialogOpen.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

/** 从第一页按用户姓名查询。 */
function handleUserQuery() {
  userQuery.pageNum = 1
  loadUsers()
}

/** 重置用户姓名筛选并回到第一页。 */
function resetUserQuery() {
  userQuery.nickName = undefined
  userQuery.pageNum = 1
  loadUsers()
}

/** 全局权限模式下禁止选择授权对象。 */
function isOptionSelectable() {
  return !globalChecked.value
}

/** 弹窗关闭后释放选择与查询状态。 */
function closeDialog() {
  resetDialogState()
}

/** 重置权限弹窗全部状态。 */
function resetDialogState() {
  Object.assign(submitForm, createEmptySubmitForm())
  activeTab.value = 'department'
  globalChecked.value = false
  Object.assign(userQuery, { pageNum: 1, pageSize: 1000, nickName: undefined })
  departmentOptions.value = []
  roleOptions.value = []
  userOptions.value = []
  selectedUserMap.clear()
}

/** 恢复三个表格的选中状态。 */
async function restoreAllSelections() {
  await nextTick()
  syncingSelection.value = true
  try {
    departmentTreeRef.value?.setCheckedKeys([])
    roleTableRef.value?.clearSelection()
    userTableRef.value?.clearSelection()
    // 三张表分别按后端返回的业务 ID 恢复当前可见行。
    departmentTreeRef.value?.setCheckedKeys(submitForm.departmentIds, false)
    // 原型采用父子联动树；直接选中父部门时，将当前全部子节点显式写入授权快照。
    submitForm.departmentIds = normalizeIds(departmentTreeRef.value?.getCheckedKeys(false) || [])
    // 角色表只恢复当前查询结果，筛选之外的角色 ID 继续保留在表单中。
    roleOptions.value.forEach(row => {
      // 只勾选当前角色查询结果内已保存的角色。
      if (submitForm.roleIds.some(id => sameId(id, row.roleId))) roleTableRef.value?.toggleRowSelection(row, true)
    })
    // 人员表只恢复当前筛选结果，其他已选人员通过 selectedUserMap 保持选择。
    userOptions.value.forEach(row => {
      // 只恢复当前筛选结果，其他人员由 selectedUserMap 继续保存。
      if (submitForm.userIds.some(id => sameId(id, row.userId))) userTableRef.value?.toggleRowSelection(row, true)
    })
  } finally {
    syncingSelection.value = false
  }
}

/** 恢复部门表当前查询结果中的选中状态。 */
async function restoreDepartmentSelection() {
  await nextTick()
  departmentTreeRef.value?.setCheckedKeys(submitForm.departmentIds, false)
  // 父部门的联动选择需要固化为具体部门 ID，判权时仍按每个部门精确匹配。
  submitForm.departmentIds = normalizeIds(departmentTreeRef.value?.getCheckedKeys(false) || [])
}

/** 恢复角色表当前查询结果中的选中状态。 */
async function restoreRoleSelection() {
  await restoreTableSelection(roleTableRef, roleOptions.value, submitForm.roleIds, 'roleId')
}

/** 恢复用户表当前筛选结果中的选中状态。 */
async function restoreUserSelection() {
  await restoreTableSelection(userTableRef, userOptions.value, submitForm.userIds, 'userId')
}

/** 按业务 ID 恢复表格勾选，不触发选择集合的反向修改。 */
async function restoreTableSelection(tableRef, rows, selectedIds, idField) {
  await nextTick()
  syncingSelection.value = true
  try {
    tableRef.value?.clearSelection()
    // 逐行匹配业务 ID，避免对象实例变化导致 Element Plus 无法回显。
    rows.forEach(row => {
      // Long ID 统一按字符串比较，兼容前后端序列化差异。
      if (selectedIds.some(id => sameId(id, row[idField]))) tableRef.value?.toggleRowSelection(row, true)
    })
  } finally {
    syncingSelection.value = false
  }
}

/** 将树形部门展开为表格行集合。 */
function flattenTree(tree) {
  const rows = []
  // 部门树按当前展示顺序展开，选择逻辑仍只作用于具体部门节点。
  tree.forEach(node => {
    rows.push(node)
    // 递归收集子部门仅用于回显，不代表部门权限向下继承。
    if (node.children?.length) rows.push(...flattenTree(node.children))
  })
  return rows
}

/** 以当前可见结果为边界合并多选，保留其他筛选结果中的选择。 */
function mergePageSelection(existingIds, visibleIds, selectedIds) {
  const retainedIds = existingIds.filter(id => !visibleIds.some(visibleId => sameId(id, visibleId)))
  return normalizeIds([...retainedIds, ...selectedIds])
}

/** 对后端返回的 ID 集合去空、去重，并兼容超过安全整数范围的字符串 ID。 */
function normalizeIds(ids) {
  const idMap = new Map()
  // 只保留有效非空 ID，并以字符串键消除 number/string 形式的重复项。
  ;(ids || []).forEach(id => {
    // 空 ID 不得进入权限保存请求。
    if (id !== null && id !== undefined && String(id).trim()) idMap.set(String(id), id)
  })
  return Array.from(idMap.values())
}

/** 使用字符串比较后端 Long ID，避免 number/string 类型差异导致无法回显。 */
function sameId(left, right) {
  return String(left) === String(right)
}

/** 在安全范围内恢复数字 ID，超出范围时保持字符串，避免精度损失。 */
function toSafeId(id) {
  const numericId = Number(id)
  return Number.isSafeInteger(numericId) ? numericId : id
}

getList()
</script>

<style scoped lang="scss">
.knowledge-auth-page {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page, #f2f3f5);
}

.list-heading,
.tab-toolbar,
.group-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-heading {
  margin-bottom: 16px;

  h2 {
    margin: 0;
    color: var(--el-text-color-primary, #303133);
    font-size: 22px;
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary, #909399);
  }
}

.list-card :deep(.el-card__body) {
  padding: 20px;
}

.permission-dialog-body {
  min-height: 0;
}

.group-summary {
  justify-content: flex-start;
  gap: 30px;
  padding: 14px;
  margin-bottom: 14px;
  background: var(--el-fill-color-light, #f5f7fa);

  span {
    color: var(--el-text-color-secondary, #909399);
  }
}

.global-permission {
  padding: 15px;
  margin-bottom: 12px;
  background: var(--el-fill-color-lighter, #fafafa);

  > span {
    margin-left: 10px;
    color: var(--el-text-color-secondary, #909399);
    font-size: 13px;
  }
}

.global-tip {
  margin-top: 8px;
  color: var(--el-color-warning, #e6a23c);
  font-size: 13px;
}

.tab-toolbar {
  min-height: 44px;

  :deep(.el-form-item) {
    margin-bottom: 10px;
  }
}

.selected-count {
  align-self: flex-start;
  padding-top: 9px;
  color: var(--el-color-primary, #409eff);
  font-size: 13px;
}

.department-tree-select {
  height: 306px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.department-tree-header {
  height: 42px;
  padding: 0 12px;
  color: var(--el-text-color-secondary, #909399);
  font-weight: 600;
  line-height: 42px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.department-tree-select :deep(.el-tree-node__content) {
  height: 52px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.department-tree-select-disabled {
  pointer-events: none;
  opacity: 0.58;
}

.other-tab-note {
  display: flex;
  gap: 18px;
  margin-top: 12px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.permission-tabs-disabled :deep(.el-tab-pane) {
  opacity: 0.58;
}

:deep(.knowledge-auth-dialog) {
  display: flex;
  height: 790px;
  flex-direction: column;

  .el-dialog__body {
    flex: 1;
    overflow: auto;
  }
}
</style>
