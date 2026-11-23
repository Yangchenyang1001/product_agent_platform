<template>
  <div class="app-container knowledge-import-page">
    <div class="page-heading">
      <div>
        <h2>知识导入</h2>
        <p>批量上传文档并跟踪每个文件的处理进度</p>
      </div>
      <el-button
        icon="Refresh"
        :loading="refreshing"
        @click="refreshRunningTasks"
      >刷新状态</el-button>
    </div>

    <el-card class="config-card" shadow="never">
      <template #header>
        <span class="card-title">导入配置</span>
      </template>
      <el-form label-position="top">
        <el-form-item label="所属知识组" required>
          <el-select
            v-model="selectedKnowledgeGroupId"
            placeholder="请选择所属知识组"
            filterable
            clearable
            class="group-select"
          >
            <el-option
              v-for="item in knowledgeGroupOptions"
              :key="item.id"
              :label="getKnowledgeGroupLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="rule-list">
        <span>支持 PDF、MD、DOCX 格式</span>
        <span>单个文件不超过 100 MB</span>
        <span>每次最多选择 20 个文件</span>
      </div>

      <el-alert
        v-if="!externalApiConfigured"
        title="外部知识库服务未配置"
        description="请联系部署人员配置外部知识库服务地址后再上传。"
        type="warning"
        :closable="false"
        show-icon
        class="service-alert"
      />

      <el-upload
        ref="uploadRef"
        drag
        multiple
        :auto-upload="false"
        :show-file-list="false"
        :disabled="!externalApiConfigured"
        accept=".pdf,.md,.docx"
        :on-change="handleFileChange"
        :on-exceed="handleFileExceed"
        :limit="MAX_FILE_COUNT"
        v-hasPermi="['kmp:knowledgeImport:import']"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或 <em>点击选择多个文件</em></div>
        <template #tip>
          <div class="el-upload__tip">允许一次选择多个文件；所选文件统一导入当前知识组</div>
        </template>
      </el-upload>
    </el-card>

    <el-card class="list-card" shadow="never">
      <div class="table-toolbar">
        <span class="card-title">文件任务队列</span>
        <span>共 {{ total }} 条平台任务<span v-if="localTaskList.length">，{{ localTaskList.length }} 条本地任务</span></span>
      </div>

      <el-table v-loading="loading" :data="taskTableList" row-key="rowKey">
        <el-table-column label="文件" min-width="230">
          <template #default="scope">
            <div class="file-cell">
              <span class="file-name">{{ scope.row.fileName || '-' }}</span>
              <span class="file-meta">{{ getFileMeta(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createUserName" width="130">
          <template #default="scope">{{ scope.row.createUserName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatCreateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getStatusMeta(scope.row).type" effect="light">
              {{ getStatusMeta(scope.row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度 / 结果" min-width="260">
          <template #default="scope">
            <import-task-status
              :task-status="scope.row.taskStatus"
              :task-info="scope.row.taskInfo"
              :failure-reason="scope.row.failureReason"
              :frontend-status="scope.row.frontendStatus"
              :upload-percent="scope.row.uploadPercent"
            />
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="110" align="center">
          <template #default="scope">{{ formatDuration(scope.row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-if="scope.row.frontendStatus === 'uploading'"
              link
              type="warning"
              @click="cancelUpload(scope.row)"
              v-hasPermi="['kmp:knowledgeImport:remove']"
            >取消</el-button>
            <el-button
              v-else-if="scope.row.frontendStatus === 'bind-failed'"
              link
              type="primary"
              @click="retryBind(scope.row)"
              v-hasPermi="['kmp:knowledgeImport:import']"
            >重试绑定</el-button>
            <el-button
              v-else-if="['init-failed', 'duplicate', 'duplicate-check-failed'].includes(scope.row.frontendStatus)"
              link
              type="danger"
              @click="removeTask(scope.row)"
            >移除</el-button>
            <el-button
              v-else-if="isLocalRemovable(scope.row)"
              link
              type="danger"
              @click="removeTask(scope.row)"
              v-hasPermi="['kmp:knowledgeImport:remove']"
            >移除</el-button>
            <el-button
              v-else-if="scope.row.taskStatus === TASK_STATUS_FAILED || scope.row.taskStatus === TASK_STATUS_PENDING"
              link
              type="danger"
              @click="removeTask(scope.row)"
              v-hasPermi="['kmp:knowledgeImport:remove']"
            >移除</el-button>
            <span v-else class="no-operation">—</span>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @pagination="loadTaskList"
      />
    </el-card>
  </div>
</template>

<script setup name="KnowledgeImport">
import axios from 'axios'
import ImportTaskStatus from '@/components/kmp/import-task-status/index.vue'
import {
  bindExternalKnowledgeImportTask,
  initKnowledgeImportTask,
  listKnowledgeImportTask,
  removeKnowledgeImportTask
} from '@/api/kmp/knowledge-import'
import {
  listKnowledgeGroupOptions
} from '@/api/kmp/knowledge-group'
import {
  isKnowledgeImportApiConfigured,
  uploadKnowledgeFile
} from '@/api/kmp/external-knowledge-import'
import { checkKnowledgeUnitDuplicate } from '@/api/kmp/knowledge-unit'
import { parseTime } from '@/utils/ruoyi'
import useUserStore from '@/store/modules/user'

const MAX_FILE_COUNT = 20
const MAX_FILE_SIZE = 100 * 1024 * 1024
const PLATFORM_REFRESH_INTERVAL = 5000
const BIND_RETRY_INTERVAL = 1500
const MAX_RETRY_COUNT = 3
const TASK_STATUS_PENDING = 0
const TASK_STATUS_PROCESSING = 2
const TASK_STATUS_SUCCESS = 3
const TASK_STATUS_FAILED = 4
const ALLOWED_EXTENSIONS = new Set(['pdf', 'md', 'docx'])

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const uploadRef = ref()
const knowledgeGroupOptions = ref([])
const selectedKnowledgeGroupId = ref()
const localTaskList = ref([])
const persistedTaskList = ref([])
const total = ref(0)
const loading = ref(false)
const refreshing = ref(false)
const externalApiConfigured = isKnowledgeImportApiConfigured()
const queryParams = reactive({ pageNum: 1, pageSize: 10 })
const abortControllerMap = new Map()
let platformRefreshTimer
let listRequestInFlight = false
let selectionTimer
let pendingFileList = []
let localTaskSequence = 0

const taskTableList = computed(() => {
  const localPlatformTaskIds = new Set(
    localTaskList.value.map(taskRow => taskRow.platformTaskId).filter(Boolean)
  )
  return [
    ...localTaskList.value.map(taskRow => ({ ...taskRow, rowKey: `local-${taskRow.localId}` })),
    ...persistedTaskList.value
      .filter(taskRow => !localPlatformTaskIds.has(taskRow.id))
      .map(taskRow => ({ ...taskRow, rowKey: `task-${taskRow.id}` }))
  ]
})

/** 加载可选知识组，并自动选择唯一默认组。 */
async function loadKnowledgeGroupOptions() {
  console.log('[知识导入] 查询知识组选项')
  const response = await listKnowledgeGroupOptions()
  knowledgeGroupOptions.value = response.data || []
  const defaultOption = knowledgeGroupOptions.value.find(item => Number(item.isDefault) === 1)
  selectedKnowledgeGroupId.value = defaultOption?.id
  console.log('[知识导入] 知识组选项查询完成：', {
    optionCount: knowledgeGroupOptions.value.length,
    defaultKnowledgeGroupId: defaultOption?.id
  })
}

/** 分页加载平台任务；页面只读取后端已经持久化的状态快照。 */
async function loadTaskList(showLoading = true) {
  if (listRequestInFlight) return
  listRequestInFlight = true
  if (showLoading) loading.value = true
  const params = { ...queryParams }
  console.log('[知识导入] 查询任务列表，请求参数：', params)
  try {
    const response = await listKnowledgeImportTask(params)
    persistedTaskList.value = response.rows || []
    total.value = Number(response.total) || 0
    console.log('[知识导入] 查询任务列表完成：', {
      total: total.value,
      currentPageRows: persistedTaskList.value.length
    })
  } finally {
    listRequestInFlight = false
    if (showLoading) loading.value = false
  }
}

/** 合并 Element Plus 连续 change 事件，确保一次选择的文件整批校验。 */
function handleFileChange(_uploadFile, uploadFileList) {
  pendingFileList = uploadFileList.map(item => item.raw).filter(Boolean)
  window.clearTimeout(selectionTimer)
  selectionTimer = window.setTimeout(() => {
    const selectedFiles = [...pendingFileList]
    pendingFileList = []
    uploadRef.value?.clearFiles()
    validateAndUploadFiles(selectedFiles)
  }, 0)
}

/** 文件数量超限时拒绝整个选择批次。 */
function handleFileExceed() {
  pendingFileList = []
  uploadRef.value?.clearFiles()
  proxy.$modal.msgError('每次最多选择20个文件')
}

/** 校验知识组、格式、大小及外部 DOCX 能力，全部通过后开始上传。 */
function validateAndUploadFiles(fileList) {
  if (!selectedKnowledgeGroupId.value) {
    proxy.$modal.msgError('请选择所属知识组')
    return
  }
  if (!externalApiConfigured) {
    proxy.$modal.msgError('外部知识库服务未配置')
    return
  }
  if (!fileList.length) return
  if (!globalThis.crypto?.subtle) {
    proxy.$modal.msgError('当前浏览器不支持文件摘要计算，请使用当前稳定版 Chrome 或 Edge')
    return
  }
  if (fileList.length > MAX_FILE_COUNT) {
    proxy.$modal.msgError('每次最多选择20个文件')
    return
  }

  const invalidMessage = getInvalidFileMessage(fileList)
  console.log('[知识导入] 文件校验完成：', {
    fileCount: fileList.length,
    passed: !invalidMessage,
    fileSummary: fileList.map(file => ({ name: file.name, size: file.size, extension: getExtension(file.name) }))
  })
  if (invalidMessage) {
    proxy.$modal.msgError(invalidMessage)
    return
  }

  const taskRows = fileList.map(createLocalTask)
  localTaskList.value.unshift(...taskRows)
  checkDuplicatesAndUpload(taskRows)
}

/** 返回整批文件中的首个明确校验错误。 */
function getInvalidFileMessage(fileList) {
  for (const file of fileList) {
    const extension = getExtension(file.name)
    if (!ALLOWED_EXTENSIONS.has(extension)) return `文件“${file.name}”格式不支持，仅支持 PDF、MD、DOCX`
    if (file.size <= 0) return `文件“${file.name}”内容为空`
    if (file.size > MAX_FILE_SIZE) return `文件“${file.name}”超过 100 MB`
    if (extension === 'docx') return '外部知识库暂不支持 DOCX 导入'
  }
  return ''
}

/** 创建仅存在于当前页面生命周期内的上传任务。 */
function createLocalTask(file) {
  return {
    localId: `${Date.now()}-${localTaskSequence += 1}`,
    file,
    fileName: file.name,
    knowledgeGroupId: selectedKnowledgeGroupId.value,
    fileSize: file.size,
    extension: getExtension(file.name),
    frontendStatus: 'checking',
    uploadPercent: 0,
    taskInfo: '',
    failureReason: '',
    createUserName: userStore.nickName || userStore.name,
    createTime: new Date(),
    startedAt: undefined,
    durationMs: undefined
  }
}

/** 逐个计算摘要并检查重复，避免并行读取多个大文件占用过多浏览器内存。 */
async function checkDuplicatesAndUpload(taskRows) {
  const uploadTaskRows = []
  // 摘要计算按文件串行执行，避免最多 20 个大文件同时占用内存。
  for (const taskRow of taskRows) {
    try {
      const fileSha256 = await calculateFileSha256(taskRow.file)
      console.log('[知识导入] 检查文件摘要：', {
        fileName: taskRow.fileName,
        fileSize: taskRow.fileSize,
        sha256Prefix: fileSha256.slice(0, 8)
      })
      const response = await checkKnowledgeUnitDuplicate(fileSha256)
      const duplicateResult = response.data || {}

      // 已存在的文件只保留本地失败行，不初始化任务，也不上传外部服务。
      if (duplicateResult.duplicate) {
        taskRow.frontendStatus = 'duplicate'
        taskRow.failureReason = `文件已导入：知识单元“${duplicateResult.knowledgeUnitName || '-'}”（ID：${duplicateResult.knowledgeUnitId || '-'}）`
        console.log('[知识导入] 文件摘要重复：', {
          fileName: taskRow.fileName,
          fileSize: taskRow.fileSize,
          sha256Prefix: fileSha256.slice(0, 8),
          knowledgeUnitId: duplicateResult.knowledgeUnitId
        })
      } else {
        taskRow.frontendStatus = 'pending'
        uploadTaskRows.push(taskRow)
      }
    } catch (error) {
      taskRow.frontendStatus = 'duplicate-check-failed'
      taskRow.failureReason = '文件重复检查失败，请移除后重新选择文件'
      console.warn('[知识导入] 文件重复检查失败：', {
        fileName: taskRow.fileName,
        fileSize: taskRow.fileSize
      })
    }
  }

  if (uploadTaskRows.length) await uploadFiles(uploadTaskRows)
}

/** 使用浏览器 Web Crypto 计算 64 位小写 SHA-256 十六进制摘要。 */
async function calculateFileSha256(file) {
  const fileBuffer = await file.arrayBuffer()
  const digestBuffer = await globalThis.crypto.subtle.digest('SHA-256', fileBuffer)
  return Array.from(new Uint8Array(digestBuffer))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

/** 分别调度每个文件，使单个文件失败不影响同批其他任务。 */
async function uploadFiles(taskRows) {
  await Promise.allSettled(taskRows.map(initAndUploadSingleFile))
}

/** 先初始化平台任务，再直传文件，最后把外部编号绑定回原任务。 */
async function initAndUploadSingleFile(taskRow) {
  taskRow.frontendStatus = 'pending'
  const initForm = {
    fileName: taskRow.fileName,
    knowledgeGroupId: taskRow.knowledgeGroupId
  }
  console.log('[知识导入] 初始化平台任务，请求参数：', {
    localId: taskRow.localId,
    fileName: taskRow.fileName,
    knowledgeGroupId: taskRow.knowledgeGroupId
  })
  try {
    const initResponse = await initKnowledgeImportTask(initForm)
    taskRow.platformTaskId = initResponse.data
    taskRow.taskStatus = TASK_STATUS_PENDING
    console.log('[知识导入] 平台任务初始化完成：', {
      localId: taskRow.localId,
      platformTaskId: taskRow.platformTaskId,
      code: initResponse.code
    })
  } catch (error) {
    taskRow.frontendStatus = 'init-failed'
    taskRow.failureReason = '初始化知识导入任务失败'
    console.warn('[知识导入] 平台任务初始化失败：', {
      localId: taskRow.localId,
      fileName: taskRow.fileName
    })
    return
  }

  const abortController = new AbortController()
  abortControllerMap.set(taskRow.localId, abortController)
  taskRow.frontendStatus = 'uploading'
  taskRow.startedAt = Date.now()
  console.log('[知识导入] 开始外部上传：', { localId: taskRow.localId, fileName: taskRow.fileName })

  try {
    const uploadResponse = await uploadKnowledgeFile(taskRow.file, {
      signal: abortController.signal,
      onUploadProgress: progressEvent => {
        if (!progressEvent.total) return
        taskRow.uploadPercent = Math.round(progressEvent.loaded * 100 / progressEvent.total)
        taskRow.durationMs = Date.now() - taskRow.startedAt
      }
    })
    const externalTaskId = String(uploadResponse?.task_id || '').trim()
    if (!externalTaskId) throw new Error('外部知识库返回的任务编号无效')
    taskRow.externalTaskId = externalTaskId
    taskRow.frontendStatus = 'binding'
    taskRow.uploadPercent = 100
    console.log('[知识导入] 外部上传受理完成：', {
      localId: taskRow.localId,
      fileName: taskRow.fileName,
      externalTaskId
    })
  } catch (error) {
    if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
      taskRow.frontendStatus = 'cancelled'
      taskRow.failureReason = ''
      console.log('[知识导入] 已取消外部上传：', { localId: taskRow.localId, fileName: taskRow.fileName })
    } else {
      taskRow.frontendStatus = 'failed'
      taskRow.failureReason = getUploadErrorMessage(error)
      console.warn('[知识导入] 外部上传失败：', {
        localId: taskRow.localId,
        fileName: taskRow.fileName,
        message: taskRow.failureReason
      })
    }
    return
  } finally {
    abortControllerMap.delete(taskRow.localId)
    taskRow.durationMs = taskRow.startedAt ? Date.now() - taskRow.startedAt : taskRow.durationMs
  }

  await bindLocalTask(taskRow)
}

/** 绑定外部任务；失败时保留两个任务编号，重试时绝不重复上传文件。 */
async function bindLocalTask(taskRow) {
  const submitForm = { externalTaskId: taskRow.externalTaskId }
  try {
    const response = await retryRequest(
      () => bindExternalKnowledgeImportTask(taskRow.platformTaskId, submitForm),
      MAX_RETRY_COUNT
    )
    taskRow.taskStatus = response.data?.taskStatus ?? TASK_STATUS_PROCESSING
    taskRow.frontendStatus = 'processing'
    taskRow.failureReason = ''
    console.log('[知识导入] 外部任务绑定完成：', {
      externalTaskId: taskRow.externalTaskId,
      platformTaskId: taskRow.platformTaskId,
      code: response.code
    })
    localTaskList.value = localTaskList.value.filter(item => item.localId !== taskRow.localId)
    queryParams.pageNum = 1
    try {
      await loadTaskList()
    } catch (error) {
      console.warn('[知识导入] 绑定成功后刷新平台列表失败：', {
        platformTaskId: taskRow.platformTaskId
      })
    }
  } catch (error) {
    taskRow.frontendStatus = 'bind-failed'
    taskRow.failureReason = '外部任务绑定失败，请重试绑定'
    console.warn('[知识导入] 外部任务绑定失败，保留平台及外部任务编号：', {
      platformTaskId: taskRow.platformTaskId,
      externalTaskId: taskRow.externalTaskId,
      fileName: taskRow.fileName
    })
  }
}

/** 手动重试外部任务绑定，不再次上传文件。 */
async function retryBind(taskRow) {
  taskRow.frontendStatus = 'binding'
  await bindLocalTask(taskRow)
}

/** 中止浏览器上传，并逻辑移除尚未绑定的待上传平台任务。 */
async function cancelUpload(taskRow) {
  const abortController = abortControllerMap.get(taskRow.localId)
  if (!abortController || taskRow.frontendStatus !== 'uploading') return
  abortController.abort()
  taskRow.frontendStatus = 'cancelled'
  if (!taskRow.platformTaskId) return
  try {
    console.log('[知识导入] 取消上传并移除平台待上传任务：', {
      platformTaskId: taskRow.platformTaskId,
      localId: taskRow.localId
    })
    await removeKnowledgeImportTask(taskRow.platformTaskId)
    taskRow.platformTaskRemoved = true
  } catch (error) {
    console.warn('[知识导入] 取消上传后移除平台任务失败：', {
      platformTaskId: taskRow.platformTaskId
    })
  }
}

/** 手动立即刷新平台任务；外部状态同步始终由平台后端负责。 */
async function refreshRunningTasks() {
  refreshing.value = true
  try {
    await loadTaskList(false)
  } finally {
    refreshing.value = false
  }
}

/** 创建唯一的平台列表刷新定时器。 */
function startPlatformRefresh() {
  stopPlatformRefresh()
  platformRefreshTimer = window.setInterval(() => {
    loadTaskList(false).catch(() => {
      console.warn('[知识导入] 自动刷新平台任务列表失败')
    })
  }, PLATFORM_REFRESH_INTERVAL)
}

/** 停止平台列表刷新，防止页面卸载后残留定时器。 */
function stopPlatformRefresh() {
  window.clearInterval(platformRefreshTimer)
  platformRefreshTimer = undefined
}

/** 移除本地取消/上传失败行，或逻辑删除平台失败任务。 */
async function removeTask(taskRow) {
  if (taskRow.localId) {
    if (taskRow.platformTaskId && !taskRow.platformTaskRemoved) {
      console.log('[知识导入] 移除本地行对应的平台待上传任务：', {
        platformTaskId: taskRow.platformTaskId,
        localId: taskRow.localId
      })
      await removeKnowledgeImportTask(taskRow.platformTaskId)
    }
    localTaskList.value = localTaskList.value.filter(item => item.localId !== taskRow.localId)
    return
  }
  await proxy.$modal.confirm(`是否确认移除文件“${taskRow.fileName || '-'}”的失败任务？`)
  console.log('[知识导入] 移除失败任务，请求 ID：', taskRow.id)
  const response = await removeKnowledgeImportTask(taskRow.id)
  console.log('[知识导入] 移除失败任务完成：', { id: taskRow.id, code: response.code })
  proxy.$modal.msgSuccess('移除成功')
  if (persistedTaskList.value.length === 1 && queryParams.pageNum > 1) queryParams.pageNum -= 1
  await loadTaskList()
}

/** 对同一绑定请求执行有限重试，禁止借重试重复上传文件。 */
async function retryRequest(requestAction, maxRetryCount) {
  let latestError
  for (let retryIndex = 0; retryIndex < maxRetryCount; retryIndex += 1) {
    try {
      return await requestAction()
    } catch (error) {
      latestError = error
      if (retryIndex < maxRetryCount - 1) await wait(BIND_RETRY_INTERVAL)
    }
  }
  throw latestError
}

function wait(delay) {
  return new Promise(resolve => window.setTimeout(resolve, delay))
}

function getUploadErrorMessage(error) {
  if (error.message === '外部知识库返回的任务编号无效') return error.message
  const detail = error.response?.data?.detail
  if (Array.isArray(detail)) {
    const validationMessage = detail
      .map(item => `${item.loc?.join('.') || 'request'}: ${item.msg || ''}`)
      .filter(Boolean)
      .join('; ')
    if (validationMessage) return validationMessage
  }
  return typeof detail === 'string' && detail.trim() ? detail.trim() : '外部知识库文件上传失败'
}

function getExtension(fileName = '') {
  return fileName.includes('.') ? fileName.split('.').pop().toLowerCase() : ''
}

function getKnowledgeGroupLabel(option) {
  const sameNameCount = knowledgeGroupOptions.value.filter(
    item => item.knowledgeGroupName === option.knowledgeGroupName
  ).length
  const defaultText = Number(option.isDefault) === 1 ? '（默认）' : ''
  const duplicateText = sameNameCount > 1 ? `（ID：${option.id}）` : ''
  return `${option.knowledgeGroupName}${duplicateText}${defaultText}`
}

function getFileMeta(taskRow) {
  const extension = (taskRow.extension || getExtension(taskRow.fileName)).toUpperCase() || '文件'
  return taskRow.fileSize === undefined ? extension : `${extension} · ${formatFileSize(taskRow.fileSize)}`
}

function formatFileSize(fileSize) {
  if (fileSize < 1024) return `${fileSize} B`
  if (fileSize < 1024 * 1024) return `${(fileSize / 1024).toFixed(1)} KB`
  return `${(fileSize / 1024 / 1024).toFixed(1)} MB`
}

function formatCreateTime(createTime) {
  return createTime ? parseTime(createTime, '{y}-{m}-{d} {h}:{i}:{s}') : '-'
}

/** 将毫秒耗时格式化为秒或“分+秒”。 */
function formatDuration(durationMs) {
  if (durationMs === undefined || durationMs === null) return '-'
  const totalSeconds = Math.max(0, Math.round(Number(durationMs) / 1000))
  if (totalSeconds < 60) return `${totalSeconds} 秒`
  return `${Math.floor(totalSeconds / 60)}分${totalSeconds % 60}秒`
}

function getStatusMeta(taskRow) {
  if (taskRow.frontendStatus) {
    return {
      pending: { label: '待上传', type: 'info' },
      checking: { label: '检查中', type: 'warning' },
      duplicate: { label: '重复文件', type: 'danger' },
      'duplicate-check-failed': { label: '检查失败', type: 'danger' },
      uploading: { label: '上传中', type: 'primary' },
      cancelled: { label: '已取消', type: 'info' },
      failed: { label: '导入失败', type: 'danger' },
      processing: { label: '处理中', type: 'warning' },
      binding: { label: '绑定中', type: 'warning' },
      'init-failed': { label: '初始化失败', type: 'danger' },
      'bind-failed': { label: '绑定失败', type: 'danger' }
    }[taskRow.frontendStatus] || { label: '处理中', type: 'warning' }
  }
  return {
    [TASK_STATUS_PENDING]: { label: '待上传', type: 'info' },
    [TASK_STATUS_PROCESSING]: { label: '处理中', type: 'warning' },
    [TASK_STATUS_SUCCESS]: { label: '导入成功', type: 'success' },
    [TASK_STATUS_FAILED]: { label: '导入失败', type: 'danger' }
  }[taskRow.taskStatus] || { label: '未知', type: 'info' }
}

function isLocalRemovable(taskRow) {
  return Boolean(taskRow.localId) && [
    'cancelled',
    'failed',
    'duplicate',
    'duplicate-check-failed'
  ].includes(taskRow.frontendStatus)
}

onMounted(async () => {
  await Promise.allSettled([loadKnowledgeGroupOptions(), loadTaskList()])
  startPlatformRefresh()
})

onBeforeUnmount(() => {
  window.clearTimeout(selectionTimer)
  abortControllerMap.forEach(controller => controller.abort())
  abortControllerMap.clear()
  stopPlatformRefresh()
})
</script>

<style scoped lang="scss">
.knowledge-import-page {
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

.config-card {
  margin-bottom: 16px;
}

.card-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.group-select {
  width: 360px;
  max-width: 100%;
}

.rule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  margin: -2px 0 18px;
  color: var(--el-text-color-secondary);
  font-size: 13px;

  span::before {
    margin-right: 7px;
    color: var(--el-color-primary);
    content: '•';
  }
}

.service-alert {
  margin-bottom: 16px;
}

.upload-icon {
  margin-bottom: 12px;
  color: var(--el-color-primary);
  font-size: 44px;
}

.table-toolbar {
  min-height: 42px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.file-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.file-name {
  overflow: hidden;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta,
.no-operation {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.list-card :deep(.el-card__body) {
  padding-top: 10px;
}

@media (max-width: 768px) {
  .page-heading {
    align-items: flex-start;
    gap: 12px;
  }

  .rule-list {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
