<template>
  <div class="import-task-status">
    <template v-if="isUploading">
      <el-progress :percentage="safeUploadPercent" :stroke-width="6" />
      <span class="latest-info">正在上传至外部知识库</span>
    </template>
    <el-popover v-else placement="top-start" :width="360" trigger="hover">
      <template #reference>
        <span class="latest-info" :class="latestInfoClass">{{ latestInfo }}</span>
      </template>
      <div class="task-detail-title">任务处理进度</div>
      <div v-if="parsedInfo.invalid" class="empty-detail">任务信息格式异常</div>
      <div v-else-if="stepList.length === 0" class="empty-detail">暂无详细信息</div>
      <div v-else class="step-list">
        <div v-for="(step, index) in stepList" :key="`${step.type}-${index}-${step.name}`" class="step-item">
          <el-icon :class="`step-icon ${step.type}`">
            <CircleCheck v-if="step.type === 'completed'" />
            <Loading v-else-if="step.type === 'processing'" />
            <CircleClose v-else />
          </el-icon>
          <span class="step-name">{{ step.name }}</span>
          <span class="step-state">{{ step.stateText }}</span>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
const props = defineProps({
  taskStatus: { type: Number, default: undefined },
  taskInfo: { type: String, default: '' },
  failureReason: { type: String, default: '' },
  frontendStatus: { type: String, default: '' },
  uploadPercent: { type: Number, default: 0 }
})

const isUploading = computed(() => props.frontendStatus === 'uploading')
const safeUploadPercent = computed(() => Math.min(100, Math.max(0, Math.round(props.uploadPercent || 0))))

/** 安全解析外部状态，异常时不向页面暴露原始 JSON。 */
const parsedInfo = computed(() => {
  if (!props.taskInfo) {
    return { status: '', doneList: [], runningList: [], error: '', invalid: false }
  }
  try {
    const taskInfo = JSON.parse(props.taskInfo)
    return {
      status: typeof taskInfo.status === 'string' ? taskInfo.status : '',
      doneList: Array.isArray(taskInfo.done_list) ? taskInfo.done_list.filter(isSafeStep) : [],
      runningList: Array.isArray(taskInfo.running_list) ? taskInfo.running_list.filter(isSafeStep) : [],
      error: safeText(taskInfo.error || taskInfo.failure_reason),
      invalid: false
    }
  } catch (error) {
    console.warn('[知识导入] 任务信息解析失败：', { length: props.taskInfo.length })
    return { status: '', doneList: [], runningList: [], error: '', invalid: true }
  }
})

const stepList = computed(() => {
  const completedSteps = parsedInfo.value.doneList.map(name => ({
    name,
    type: 'completed',
    stateText: '已完成'
  }))
  const processingSteps = parsedInfo.value.runningList.map(name => ({
    name,
    type: parsedInfo.value.status === 'failed' ? 'failed' : 'processing',
    stateText: parsedInfo.value.status === 'failed' ? '失败' : '处理中'
  }))
  return [...completedSteps, ...processingSteps]
})

const latestInfo = computed(() => {
  if (props.failureReason) return props.failureReason
  if (parsedInfo.value.error) return parsedInfo.value.error
  if (parsedInfo.value.invalid) return '任务信息格式异常'
  if (props.frontendStatus === 'cancelled') return '上传已取消'
  if (props.frontendStatus === 'failed') return '外部知识库文件上传失败'
  if (props.frontendStatus === 'checking') return '正在计算摘要并检查重复文件'
  if (props.frontendStatus === 'duplicate') return '文件已存在，不再初始化或上传'
  if (props.frontendStatus === 'duplicate-check-failed') return '文件重复检查失败'
  if (props.frontendStatus === 'init-failed') return '初始化知识导入任务失败'
  if (props.frontendStatus === 'binding') return '正在绑定外部任务'
  if (props.frontendStatus === 'bind-failed') return '外部任务绑定失败，可重试绑定'
  if (parsedInfo.value.runningList.length) {
    return `正在处理：${parsedInfo.value.runningList.at(-1)}`
  }
  if (props.taskStatus === 4 || parsedInfo.value.status === 'failed') {
    return '外部知识库任务处理失败'
  }
  if (props.taskStatus === 3 || parsedInfo.value.status === 'completed') {
    return '已完成全部处理步骤'
  }
  if (parsedInfo.value.doneList.length) {
    return `已完成：${parsedInfo.value.doneList.at(-1)}`
  }
  if (props.frontendStatus === 'pending') return '等待开始导入'
  return '暂无详细信息'
})

const latestInfoClass = computed(() => ({
  failed: props.taskStatus === 4 || ['failed', 'duplicate', 'duplicate-check-failed'].includes(props.frontendStatus),
  cancelled: props.frontendStatus === 'cancelled'
}))

function isSafeStep(step) {
  return typeof step === 'string' && step.trim().length > 0
}

function safeText(value) {
  return typeof value === 'string' ? value.trim() : ''
}
</script>

<style scoped lang="scss">
.import-task-status {
  min-width: 0;
}

.latest-info {
  display: block;
  overflow: hidden;
  color: var(--el-text-color-regular);
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;

  &.failed {
    color: var(--el-color-danger);
  }

  &.cancelled {
    color: var(--el-text-color-secondary);
  }
}

.task-detail-title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.empty-detail {
  color: var(--el-text-color-secondary);
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.step-icon {
  &.completed {
    color: var(--el-color-success);
  }

  &.processing {
    color: var(--el-color-primary);
  }

  &.failed {
    color: var(--el-color-danger);
  }
}

.step-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-state {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:deep(.el-progress__text) {
  min-width: 38px;
  font-size: 12px !important;
}
</style>
