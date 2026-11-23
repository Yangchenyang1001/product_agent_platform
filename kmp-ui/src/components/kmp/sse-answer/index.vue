<template>
  <article class="assistant-answer" :class="`is-${status}`">
    <header class="answer-header">
      <span>知识助手</span>
      <el-tag v-if="statusText" :type="statusTagType" size="small" effect="light">
        {{ statusText }}
      </el-tag>
    </header>

    <div v-if="displayText" class="answer-text">{{ displayText }}</div>
    <div v-else-if="status === 'streaming'" class="typing-placeholder">
      <i></i><i></i><i></i>
    </div>
    <div v-else class="answer-text empty-answer">（已完成，但未返回答案）</div>

    <div v-if="safeImageList.length" class="answer-images">
      <div v-for="imageItem in safeImageList" :key="imageItem.url" class="image-item">
        <img
          v-show="!failedImageSet.has(imageItem.url)"
          :src="imageItem.url"
          loading="lazy"
          alt="参考图片"
          referrerpolicy="no-referrer"
          @error="handleImageError(imageItem.url)"
        />
        <a
          v-if="failedImageSet.has(imageItem.url)"
          :href="imageItem.url"
          target="_blank"
          rel="noopener noreferrer"
        >图片加载失败，打开原地址</a>
        <button v-else type="button" class="image-preview-button" @click="openImagePreview(imageItem.url)">
          查看原图
        </button>
      </div>
    </div>

    <el-dialog
      v-model="previewVisible"
      title="原图预览"
      class="image-preview-dialog"
      width="auto"
      center
      append-to-body
    >
      <div class="image-preview-stage" @wheel.prevent="zoomPreviewImage">
        <img
          class="image-preview"
          :src="previewImageUrl"
          :style="{ transform: `scale(${previewZoom})` }"
          alt="参考图片原图"
          referrerpolicy="no-referrer"
        />
      </div>
      <div class="image-preview-zoom">滚轮缩放 · {{ Math.round(previewZoom * 100) }}%</div>
    </el-dialog>

    <el-collapse v-if="hasProgress" class="answer-progress">
      <el-collapse-item :title="progressTitle" name="progress">
        <div v-if="!progressSteps.length" class="progress-empty">暂无详细进度</div>
        <div v-for="step in progressSteps" :key="`${step.state}-${step.name}`" class="progress-step">
          <el-icon :class="step.state">
            <CircleCheck v-if="step.state === 'completed'" />
            <Loading v-else class="is-loading" />
          </el-icon>
          <span>{{ step.name }}</span>
          <small>{{ step.state === 'completed' ? '已完成' : '处理中' }}</small>
        </div>
      </el-collapse-item>
    </el-collapse>

    <footer v-if="status !== 'restricted'" class="answer-footer">
      <span v-if="durationMs !== null">响应 {{ formatDuration(durationMs) }}</span>
      <span>{{ time || '--:--' }}</span>
      <div v-if="canFeedback" class="feedback-actions">
        <el-button
          link
          :type="feedbackStatus === 1 ? 'success' : 'info'"
          :loading="feedbackLoading"
          @click="submitFeedback(1)"
        >有帮助</el-button>
        <el-button
          link
          :type="feedbackStatus === 2 ? 'danger' : 'info'"
          :loading="feedbackLoading"
          @click="submitFeedback(2)"
        >无帮助</el-button>
      </div>
      <el-button
        v-if="status === 'completed' && displayText"
        link
        type="primary"
        :icon="CopyDocument"
        @click="$emit('copy', displayText)"
      >复制</el-button>
    </footer>
  </article>
</template>

<script setup>
import { CircleCheck, CopyDocument, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  answerText: { type: String, default: '' },
  imageUrls: { type: Array, default: () => [] },
  status: { type: String, default: 'streaming' },
  time: { type: String, default: '' },
  durationMs: { type: Number, default: null },
  source: { type: String, default: 'ai' },
  requestId: { type: String, default: '' },
  answerType: { type: Number, default: null },
  feedbackStatus: { type: Number, default: 0 },
  feedbackLoading: { type: Boolean, default: false },
  progress: {
    type: Object,
    default: () => ({ status: '', doneList: [], runningList: [] })
  }
})

const emit = defineEmits(['copy', 'feedback'])

const failedImageSet = reactive(new Set())
const previewImageUrl = ref('')
const previewZoom = ref(1)
const previewVisible = computed({
  get: () => Boolean(previewImageUrl.value),
  set: visible => {
    if (!visible) previewImageUrl.value = ''
  }
})

function openImagePreview(imageUrl) {
  previewImageUrl.value = imageUrl
  previewZoom.value = 1
}

function zoomPreviewImage(event) {
  const zoomStep = 0.1
  const nextZoom = previewZoom.value + (event.deltaY < 0 ? zoomStep : -zoomStep)
  previewZoom.value = Math.min(3, Math.max(0.5, Number(nextZoom.toFixed(1))))
}

const parsedAnswer = computed(() => parseAnswer(props.answerText))
const displayText = computed(() => parsedAnswer.value.text)
const safeImageList = computed(() => {
  const candidateUrls = [...parsedAnswer.value.imageUrls, ...props.imageUrls]
  const uniqueUrls = []
  const seenUrls = new Set()

  // 图片可能同时出现在正文与终态数组中，按第一次出现的位置去重。
  for (const candidateUrl of candidateUrls) {
    const safeUrl = normalizeImageUrl(candidateUrl)
    if (safeUrl && !seenUrls.has(safeUrl)) {
      seenUrls.add(safeUrl)
      uniqueUrls.push({ url: safeUrl })
    }
  }
  return uniqueUrls
})

const statusText = computed(() => ({
  streaming: '回答中',
  completed: '回答完成',
  interrupted: '响应中断',
  failed: '回答失败',
  restricted: '访问受限'
}[props.status] || ''))

const statusTagType = computed(() => ({
  streaming: 'primary',
  completed: 'success',
  interrupted: 'warning',
  failed: 'danger',
  restricted: 'warning'
}[props.status] || 'info'))

const progressSteps = computed(() => {
  const doneList = Array.isArray(props.progress.doneList) ? props.progress.doneList : []
  const runningList = Array.isArray(props.progress.runningList) ? props.progress.runningList : []
  return [
    ...doneList.map(name => ({ name, state: 'completed' })),
    ...runningList.map(name => ({ name, state: 'processing' }))
  ]
})

const hasProgress = computed(() => props.status === 'streaming' || progressSteps.value.length > 0)
const canFeedback = computed(() =>
  props.source === 'ai' &&
  props.status === 'completed' &&
  Boolean(displayText.value) &&
  props.answerType === 0 &&
  Boolean(props.requestId)
)
const progressTitle = computed(() =>
  `处理进度（已完成 ${props.progress.doneList?.length || 0}，进行中 ${props.progress.runningList?.length || 0}）`
)

/** 解析图片标记、Markdown 图片和正文裸图片地址，同时保留安全的纯文本答案。 */
function parseAnswer(answerText) {
  const rawText = String(answerText || '')
  const imageUrls = []
  const imageMarkerPattern = /【\s*图片\s*】|\[\s*图片\s*]/g
  let markerMatch
  let lastMarker

  while ((markerMatch = imageMarkerPattern.exec(rawText)) !== null) {
    lastMarker = markerMatch
  }

  let textPart = rawText
  if (lastMarker) {
    textPart = rawText.slice(0, lastMarker.index)
    imageUrls.push(...extractLooseUrls(rawText.slice(lastMarker.index + lastMarker[0].length)))
  }

  textPart = textPart.replace(/!\[[^\]]*]\((https?:\/\/[^)]+)\)/gi, (_, url) => {
    imageUrls.push(url)
    return ''
  })
  textPart = textPart.replace(/https?:\/\/[^\s<>\]）)"']+/gi, url => {
    if (normalizeImageUrl(url)) {
      imageUrls.push(url)
      return ''
    }
    return url
  })
  return { text: textPart.trim(), imageUrls }
}

/** 从兼容格式文本中提取 HTTP(S) 地址。 */
function extractLooseUrls(text) {
  return String(text || '').match(/https?:\/\/[^\s<>\]）)"']+/gi) || []
}

/** 仅允许浏览器可安全导航的 HTTP(S) 图片扩展名。 */
function normalizeImageUrl(candidateUrl) {
  const trimmedUrl = String(candidateUrl || '')
    .trim()
    .replace(/^[<（(【\[]+/, '')
    .replace(/[>，。,;；】\]）)"']+$/, '')
    .replace(/ /g, '%20')
  try {
    const parsedUrl = new URL(trimmedUrl)
    const allowedProtocol = parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:'
    const imagePath = decodeURIComponent(parsedUrl.pathname).toLowerCase()
    return allowedProtocol && /\.(png|jpe?g|gif|webp|bmp|svg)$/.test(imagePath) ? parsedUrl.href : ''
  } catch (error) {
    return ''
  }
}

/** 图片失败只隐藏预览，原地址仍供用户按需打开。 */
function handleImageError(imageUrl) {
  failedImageSet.add(imageUrl)
  console.warn('[知识查询] 图片加载失败：', { failedCount: failedImageSet.size })
}

/** 将反馈意图交给页面提交，组件只负责展示和防重复点击。 */
function submitFeedback(feedbackStatus) {
  if (!canFeedback.value || props.feedbackLoading) return
  emit('feedback', feedbackStatus)
}

/** 将毫秒耗时转换为页面可读秒数。 */
function formatDuration(duration) {
  return `${(Math.max(0, duration) / 1000).toFixed(1)} 秒`
}
</script>

<style scoped lang="scss">
.assistant-answer {
  width: 100%;
  box-sizing: border-box;
  padding: 18px 20px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.answer-header,
.answer-footer,
.progress-step {
  display: flex;
  align-items: center;
}

.answer-header {
  justify-content: space-between;
  margin-bottom: 14px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.answer-text {
  color: var(--el-text-color-primary);
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.empty-answer,
.progress-empty {
  color: var(--el-text-color-secondary);
}

.typing-placeholder {
  display: flex;
  gap: 5px;
  padding: 8px 0;

  i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--el-color-primary);
    animation: typing 1.2s infinite ease-in-out;
  }

  i:nth-child(2) { animation-delay: 0.15s; }
  i:nth-child(3) { animation-delay: 0.3s; }
}

.answer-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.image-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;

  img {
    width: 100%;
    height: 150px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    object-fit: contain;
    background: var(--el-fill-color-light);
  }

  a {
    color: var(--el-color-primary);
    font-size: 12px;
    text-align: center;
  }

  .image-preview-button {
    padding: 0;
    border: 0;
    background: none;
    color: var(--el-color-primary);
    cursor: pointer;
    font: inherit;
    font-size: 12px;
    text-align: center;
  }
}

:global(.image-preview-dialog.el-dialog) {
  width: fit-content;
  max-width: 92vw;
  margin: 8vh auto 0 !important;
}

.image-preview {
  display: block;
  max-width: min(86vw, 880px);
  max-height: 76vh;
  object-fit: contain;
  transform-origin: center;
  transition: transform 120ms ease-out;
}

.image-preview-stage {
  display: grid;
  place-items: center;
  max-width: 86vw;
  max-height: 76vh;
  overflow: visible;
  cursor: zoom-in;
}

.image-preview-zoom {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
}

.answer-progress {
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);

  :deep(.el-collapse-item__header) {
    height: 40px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.progress-step {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 8px;
  padding: 4px 0;

  .completed { color: var(--el-color-success); }
  .processing { color: var(--el-color-primary); }
  small { color: var(--el-text-color-secondary); }
}

.answer-footer {
  gap: 16px;
  margin-top: 14px;
  color: var(--el-text-color-secondary);
  font-size: 12px;

  .el-button { margin-left: auto; }
}

.feedback-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;

  .el-button { margin-left: 0; }
}

.feedback-actions + .el-button {
  margin-left: 0;
}

.is-restricted {
  border-color: var(--el-color-warning-light-7);
  background: var(--el-color-warning-light-9);
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}
</style>
