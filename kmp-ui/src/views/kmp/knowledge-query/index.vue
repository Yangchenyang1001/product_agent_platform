<template>
  <div class="app-container knowledge-query-page">
    <div class="page-heading">
      <div>
        <h2>知识查询</h2>
        <p>基于当前账号有权访问的知识内容进行智能问答</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="createNewConversation">新建会话</el-button>
    </div>

    <el-alert
      v-if="!queryApiConfigured"
      title="外部知识库服务未配置，请联系管理员配置查询服务地址。"
      type="warning"
      show-icon
      :closable="false"
      class="service-alert"
    />

    <div class="query-layout">
      <el-card class="conversation-card" shadow="never">
        <template #header>
          <div class="conversation-header">
            <div class="conversation-title">
              <span class="online-dot"></span>
              <strong>智能知识问答</strong>
              <el-tag v-if="activeSessionId" type="info" effect="plain">会话 #{{ activeSessionId }}</el-tag>
            </div>
            <el-button
              :icon="Delete"
              :disabled="!activeSessionId || sending"
              @click="clearCurrentConversation"
            >清空会话</el-button>
          </div>
        </template>

        <div ref="messagePanelRef" class="message-panel">
          <div v-if="messages.length === 0" class="empty-conversation">
            <el-icon><ChatDotRound /></el-icon>
            <h3>{{ activeSessionId ? '当前会话暂无问答记录' : '开始一次知识查询' }}</h3>
            <p>输入业务问题，知识助手将基于您有权访问的知识生成回答。</p>
          </div>

          <div
            v-for="message in messages"
            :key="message.localId"
            class="message-row"
            :class="message.role"
          >
            <template v-if="message.role === 'user'">
              <div class="user-message">
                <div class="user-text">{{ message.text }}</div>
                <time>{{ message.time }}</time>
              </div>
              <div class="user-avatar">我</div>
            </template>
            <template v-else>
              <div class="assistant-avatar"><el-icon><Service /></el-icon></div>
              <sse-answer
                :answer-text="message.text"
                :image-urls="message.imageUrls"
                :status="message.status"
                :time="message.time"
                :duration-ms="message.durationMs"
                :progress="message.progress"
                :source="message.source"
                :request-id="message.requestId"
                :answer-type="message.answerType"
                :feedback-status="message.feedbackStatus"
                :feedback-loading="message.feedbackLoading"
                @copy="copyAnswer"
                @feedback="feedbackStatus => submitFeedback(message, feedbackStatus)"
              />
            </template>
          </div>
        </div>

        <section v-if="recommendationList.length" class="faq-recommendations" aria-label="热门 FAQ">
          <div class="faq-section-title">
            <span><el-icon><Star /></el-icon> 热门 FAQ</span>
            <small>点选后直接查看标准答案</small>
          </div>
          <div class="faq-chip-list">
            <button
              v-for="faq in recommendationList"
              :key="faq.id"
              type="button"
              class="faq-chip"
              :disabled="queryBusy"
              @click="selectFaq(faq, 'recommendation')"
            >{{ faq.standardQuestion }}</button>
          </div>
        </section>

        <div class="question-composer">
          <div class="question-input-wrap">
            <el-input
              v-model="question"
              type="textarea"
              resize="none"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请输入您想查询的问题，Enter 发送，Shift+Enter 换行"
              :disabled="queryBusy || !hasQueryPermission || !queryApiConfigured"
              @input="handleQuestionInput"
              @keydown="handleQuestionKeydown"
            />
            <div
              v-if="suggestionLoading || suggestionList.length"
              class="faq-suggestion-panel"
              role="listbox"
              aria-label="FAQ 联想"
            >
              <div v-if="suggestionLoading" class="faq-suggestion-loading">
                <el-icon class="is-loading"><Loading /></el-icon> 正在查询 FAQ
              </div>
              <template v-else>
                <button
                  v-for="(faq, index) in suggestionList"
                  :key="faq.id"
                  type="button"
                  class="faq-suggestion-item"
                  :class="{ active: index === activeSuggestionIndex }"
                  role="option"
                  :aria-selected="index === activeSuggestionIndex"
                  @mouseenter="activeSuggestionIndex = index"
                  @click="selectFaq(faq, 'suggestion')"
                >{{ faq.standardQuestion }}</button>
              </template>
            </div>
          </div>
          <el-button
            class="send-button"
            type="primary"
            :icon="Promotion"
            :loading="sending"
            :disabled="!canSend"
            @click="sendQuestion"
          >发送</el-button>
        </div>
        <div class="composer-tip">
          <el-icon><Lock /></el-icon>
          回答仅基于您当前有权访问的知识内容；查询结果请结合实际业务复核。
        </div>
      </el-card>

      <el-card class="history-card" shadow="never">
        <template #header>
          <div class="history-header">
            <strong><el-icon><Clock /></el-icon> 历史会话</strong>
            <el-button link type="primary" :icon="Plus" @click="createNewConversation">新建</el-button>
          </div>
        </template>

        <div class="history-search">
          <el-input
            v-model="sessionKeyword"
            :prefix-icon="Search"
            clearable
            maxlength="255"
            placeholder="搜索历史会话"
            @input="handleSessionSearch"
            @clear="handleSessionSearch"
          />
        </div>

        <div class="history-list" @scroll="handleHistoryScroll">
          <template v-for="group in groupedSessions" :key="group.key">
            <div v-if="group.items.length" class="history-group">
              <div class="group-title">{{ group.label }}</div>
              <div
                v-for="session in group.items"
                :key="session.id"
                class="session-item"
                :class="{ active: String(session.id) === activeSessionId }"
                role="button"
                tabindex="0"
                @click="switchSession(session.id)"
                @keydown.enter="switchSession(session.id)"
                @keydown.space.prevent="switchSession(session.id)"
              >
                <span class="session-main">
                  <span class="session-title">{{ session.sessionTitle }}</span>
                  <time>{{ formatSessionTime(session.createTime) }}</time>
                </span>
                <el-tag v-if="String(session.id) === activeSessionId" size="small">当前</el-tag>
                <el-button
                  v-else
                  link
                  type="danger"
                  :icon="Delete"
                  title="删除会话"
                  @click.stop="removeSession(session)"
                />
              </div>
            </div>
          </template>
          <el-empty v-if="!sessionLoading && sessionList.length === 0" description="暂无历史会话" :image-size="72" />
          <div v-if="sessionLoading" class="history-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中</div>
        </div>

        <div class="history-footer">
          <span>共 {{ sessionTotal }} 个会话</span>
          <el-button link type="danger" :icon="Delete" :disabled="sessionTotal === 0" @click="clearAllSessions">
            清理历史
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup name="KnowledgeQuery">
import {
  ChatDotRound,
  Clock,
  Delete,
  Loading,
  Lock,
  Plus,
  Promotion,
  Search,
  Service,
  Star
} from '@element-plus/icons-vue'
import SseAnswer from '@/components/kmp/sse-answer/index.vue'
import {
  clearKnowledgeQuerySessions,
  createKnowledgeQuerySession,
  getFaqAnswer,
  listKnowledgeQuerySessions,
  listRecommendedFaqs,
  listSuggestedFaqs,
  removeKnowledgeQuerySession,
  updateKnowledgeQueryFeedback
} from '@/api/kmp/knowledge-query'
import {
  clearExternalKnowledgeQueryHistory,
  getExternalKnowledgeQueryHistory,
  isKnowledgeQueryApiConfigured,
  openKnowledgeQueryStream,
  submitExternalKnowledgeQuery
} from '@/api/kmp/external-knowledge-query'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const messagePanelRef = ref()
const activeSessionId = ref('')
const sessionList = ref([])
const sessionTotal = ref(0)
const sessionKeyword = ref('')
const sessionPageNum = ref(1)
const sessionLoading = ref(false)
const messages = ref([])
const question = ref('')
const sending = ref(false)
const faqSelecting = ref(false)
const latestItemName = ref('')
const recommendationList = ref([])
const suggestionList = ref([])
const suggestionLoading = ref(false)
const activeSuggestionIndex = ref(-1)
const streamState = ref('idle')
const eventSource = shallowRef(null)
const requestStartedAt = ref(null)
const queryApiConfigured = isKnowledgeQueryApiConfigured()
let searchTimer = null
let faqSuggestionTimer = null
let historyRequestVersion = 0
let recommendationRequestVersion = 0
let suggestionRequestVersion = 0
let conversationVersion = 0
let messageSequence = 0
let finalReceived = false
let faqRetryContext = null

const hasQueryPermission = computed(() =>
  userStore.permissions.includes('*:*:*') || userStore.permissions.includes('kmp:knowledgeQuery:query')
)
const queryBusy = computed(() => sending.value || faqSelecting.value)
const canSend = computed(() =>
  Boolean(question.value.trim()) &&
  question.value.trim().length <= 500 &&
  !queryBusy.value &&
  hasQueryPermission.value &&
  queryApiConfigured
)

const groupedSessions = computed(() => {
  const today = getShanghaiDateKey(new Date())
  const yesterdayDate = new Date(Date.now() - 24 * 60 * 60 * 1000)
  const yesterday = getShanghaiDateKey(yesterdayDate)
  const groups = [
    { key: 'today', label: '今天', items: [] },
    { key: 'yesterday', label: '昨天', items: [] },
    { key: 'earlier', label: '更早', items: [] }
  ]

  // 服务端时间采用 Asia/Shanghai，直接比较日期部分可避免终端时区漂移。
  for (const session of sessionList.value) {
    const sessionDate = String(session.createTime || '').slice(0, 10)
    if (sessionDate === today) groups[0].items.push(session)
    else if (sessionDate === yesterday) groups[1].items.push(session)
    else groups[2].items.push(session)
  }
  return groups
})

/** 只保留后端允许公开的 FAQ 字段，避免异常数据进入交互状态。 */
function normalizeFaqList(candidateList, limit) {
  if (!Array.isArray(candidateList)) return []
  const faqList = []
  const faqIdSet = new Set()
  // 同一问题簇只展示一次，并保持后端返回的排序。
  for (const candidate of candidateList) {
    const faqId = Number(candidate?.id)
    const standardQuestion = typeof candidate?.standardQuestion === 'string'
      ? candidate.standardQuestion.trim()
      : ''
    if (Number.isSafeInteger(faqId) && faqId > 0 && standardQuestion && !faqIdSet.has(faqId)) {
      faqIdSet.add(faqId)
      faqList.push({ id: faqId, standardQuestion })
    }
    if (faqList.length >= limit) break
  }
  return faqList
}

/** 请求最新知识单元的热门 FAQ，旧会话响应不得覆盖当前页面。 */
async function loadFaqRecommendations(itemName) {
  const normalizedItemName = String(itemName || '').trim()
  const currentRequestVersion = ++recommendationRequestVersion
  recommendationList.value = []
  if (!normalizedItemName || !hasQueryPermission.value) return
  console.log('[知识查询] 查询热门 FAQ：', { apiType: 'recommendation' })
  try {
    const response = await listRecommendedFaqs(normalizedItemName)
    if (currentRequestVersion !== recommendationRequestVersion || normalizedItemName !== latestItemName.value) return
    recommendationList.value = normalizeFaqList(response.data, 5)
    console.log('[知识查询] 热门 FAQ 查询完成：', {
      apiType: 'recommendation',
      resultCount: recommendationList.value.length,
      status: response.code
    })
  } catch (error) {
    if (currentRequestVersion === recommendationRequestVersion) recommendationList.value = []
    console.warn('[知识查询] 热门 FAQ 查询失败：', { apiType: 'recommendation' })
  }
}

/** 关闭联想并使所有已发出的联想请求过期。 */
function clearFaqSuggestions() {
  clearTimeout(faqSuggestionTimer)
  faqSuggestionTimer = null
  suggestionRequestVersion += 1
  suggestionList.value = []
  suggestionLoading.value = false
  activeSuggestionIndex.value = -1
}

/** 输入满 5 个字符后防抖 300 毫秒查询 FAQ 联想。 */
function handleQuestionInput() {
  clearFaqSuggestions()
  const keyword = question.value.trim()
  if (keyword.length < 5 || !hasQueryPermission.value) return
  const currentRequestVersion = suggestionRequestVersion
  faqSuggestionTimer = setTimeout(async () => {
    if (currentRequestVersion !== suggestionRequestVersion || keyword !== question.value.trim()) return
    suggestionLoading.value = true
    console.log('[知识查询] 查询 FAQ 联想：', { apiType: 'suggestion' })
    try {
      const response = await listSuggestedFaqs(keyword)
      if (currentRequestVersion !== suggestionRequestVersion || keyword !== question.value.trim()) return
      suggestionList.value = normalizeFaqList(response.data, 10)
      activeSuggestionIndex.value = -1
      console.log('[知识查询] FAQ 联想查询完成：', {
        apiType: 'suggestion',
        resultCount: suggestionList.value.length,
        status: response.code
      })
    } catch (error) {
      if (currentRequestVersion === suggestionRequestVersion) suggestionList.value = []
      console.warn('[知识查询] FAQ 联想查询失败：', { apiType: 'suggestion' })
    } finally {
      if (currentRequestVersion === suggestionRequestVersion) suggestionLoading.value = false
    }
  }, 300)
}

/** 分页加载当前用户的会话索引，翻页合并时按 ID 去重。 */
async function loadSessions(reset = false, selectFirst = true) {
  if (sessionLoading.value) return
  if (reset) sessionPageNum.value = 1
  sessionLoading.value = true
  const params = {
    pageNum: sessionPageNum.value,
    pageSize: 100,
    sessionTitle: sessionKeyword.value.trim() || undefined
  }
  console.log('[知识查询] 加载会话列表：', {
    pageNum: params.pageNum,
    searching: Boolean(params.sessionTitle)
  })
  try {
    const response = await listKnowledgeQuerySessions(params)
    const responseRows = Array.isArray(response.rows) ? response.rows : []
    const mergedRows = reset ? responseRows : [...sessionList.value, ...responseRows]
    const sessionMap = new Map()
    for (const session of mergedRows) sessionMap.set(String(session.id), session)
    sessionList.value = [...sessionMap.values()]
    sessionTotal.value = Number(response.total) || 0
    console.log('[知识查询] 会话列表加载完成：', {
      returnedCount: responseRows.length,
      total: sessionTotal.value
    })

    // 首次进入页面默认恢复最新会话，搜索时也只选择搜索结果中的有效项。
    if (selectFirst && reset && !activeSessionId.value && sessionList.value.length) {
      await switchSession(sessionList.value[0].id)
    }
  } finally {
    sessionLoading.value = false
  }
}

/** 标题输入停止 300 毫秒后执行后端模糊查询。 */
function handleSessionSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    closeEventSource()
    resetFaqContext()
    activeSessionId.value = ''
    messages.value = []
    loadSessions(true)
  }, 300)
}

/** 历史侧栏滚动到底后加载下一页。 */
function handleHistoryScroll(event) {
  const panel = event.currentTarget
  const reachedBottom = panel.scrollTop + panel.clientHeight >= panel.scrollHeight - 24
  if (reachedBottom && sessionList.value.length < sessionTotal.value && !sessionLoading.value) {
    sessionPageNum.value += 1
    loadSessions(false, false)
  }
}

/** 新建会话仅重置页面，首次有效提问时才真正创建索引。 */
function createNewConversation() {
  conversationVersion += 1
  historyRequestVersion += 1
  closeEventSource()
  resetFaqContext()
  activeSessionId.value = ''
  messages.value = []
  question.value = ''
  streamState.value = 'idle'
}

/** 切换会话并只接收最后一次切换对应的历史响应。 */
async function switchSession(sessionId) {
  const targetSessionId = String(sessionId)
  if (!targetSessionId || targetSessionId === activeSessionId.value && messages.value.length) return
  conversationVersion += 1
  const currentHistoryVersion = ++historyRequestVersion
  closeEventSource()
  resetFaqContext()
  activeSessionId.value = targetSessionId
  messages.value = []
  streamState.value = 'idle'
  if (!queryApiConfigured) return
  console.log('[知识查询] 查询外部会话历史：', { sessionId: targetSessionId })
  try {
    const response = await getExternalKnowledgeQueryHistory(targetSessionId, 50)
    // 快速切换时丢弃旧请求结果，避免消息串入当前会话。
    if (currentHistoryVersion !== historyRequestVersion || targetSessionId !== activeSessionId.value) return
    const historyItems = Array.isArray(response.items) ? response.items : []
    messages.value = historyItems
      .filter(item => item.role === 'user' || item.role === 'assistant')
      .map(item => createHistoryMessage(item))
    latestItemName.value = findLatestItemName(historyItems)
    await loadFaqRecommendations(latestItemName.value)
    console.log('[知识查询] 外部会话历史加载完成：', {
      sessionId: targetSessionId,
      messageCount: messages.value.length
    })
    scrollMessagesToBottom()
  } catch (error) {
    if (targetSessionId === activeSessionId.value) proxy.$modal.msgError('会话历史加载失败，请稍后重试')
  }
}

/** 首问以截断后的问题作为标题创建平台会话索引。 */
async function ensureSession(questionText) {
  if (activeSessionId.value) return activeSessionId.value
  console.log('[知识查询] 创建会话开始')
  const response = await createKnowledgeQuerySession({ sessionTitle: questionText.slice(0, 255) })
  const createdSessionId = String(response.data || '')
  if (!/^\d+$/.test(createdSessionId) || Number(createdSessionId) <= 0) {
    throw new Error('平台未返回有效的会话 ID')
  }
  activeSessionId.value = createdSessionId
  console.log('[知识查询] 创建会话完成：', { sessionId: createdSessionId })
  await loadSessions(true, false)
  return createdSessionId
}

/** 校验并提交问题，然后使用任务 ID 建立 SSE 增量连接。 */
async function sendQuestion() {
  const questionText = question.value.trim()
  if (!canSend.value || !questionText) return
  clearFaqSuggestions()
  const operationVersion = conversationVersion
  sending.value = true
  streamState.value = 'connecting'
  finalReceived = false
  requestStartedAt.value = performance.now()
  try {
    const sessionId = await ensureSession(questionText)
    // 创建会话期间用户可能主动重置页面，此时不再启动外部查询。
    if (operationVersion !== conversationVersion || sessionId !== activeSessionId.value) return
    question.value = ''
    messages.value.push(createMessage('user', questionText, 'completed'))
    // SSE 回调会持续修改该对象，必须先转为响应式代理再保存和传递。
    const assistantMessage = reactive(createMessage('assistant', '', 'streaming'))
    messages.value.push(assistantMessage)
    scrollMessagesToBottom()

    const submitForm = {
      query: questionText,
      session_id: sessionId,
      is_stream: true,
      user_id: Number(userStore.id)
    }
    console.log('[知识查询] 提交外部查询：', { sessionId, streaming: true })
    const response = await submitExternalKnowledgeQuery(submitForm)
    if (operationVersion !== conversationVersion || sessionId !== activeSessionId.value) return
    if (String(response.session_id) !== sessionId) throw new Error('外部服务返回的会话 ID 不一致')
    if (!response.task_id) throw new Error('外部服务未返回任务 ID')
    console.log('[知识查询] 外部查询提交完成：', { sessionId, taskId: response.task_id })
    connectKnowledgeQueryStream(response.task_id, sessionId, assistantMessage, operationVersion)
  } catch (error) {
    // 只有仍处于当前会话的任务可以写入失败状态。
    if (operationVersion === conversationVersion) {
      const assistantMessage = messages.value.at(-1)
      if (assistantMessage?.role === 'assistant' && assistantMessage.status === 'streaming') {
        assistantMessage.status = 'failed'
        assistantMessage.text = '服务暂时不可用，可重新提问。'
      } else if (messages.value.at(-1)?.role === 'user') {
        messages.value.push(createMessage('assistant', '服务暂时不可用，可重新提问。', 'failed'))
      } else {
        proxy.$modal.msgError(error.message || '会话创建失败，请稍后重试')
      }
      streamState.value = 'failed'
    }
  } finally {
    // 已建立 SSE 时由终态回调恢复按钮，其他路径在此恢复。
    if (!eventSource.value) sending.value = false
  }
}

/** 为当前助手消息绑定 SSE 的四类事件。 */
function connectKnowledgeQueryStream(taskId, sessionId, assistantMessage, operationVersion) {
  closeEventSource(false)
  eventSource.value = openKnowledgeQueryStream(taskId, {
    onReady: event => handleReady(event, taskId, sessionId, operationVersion),
    onProgress: event => handleProgress(event, assistantMessage, sessionId, operationVersion),
    onDelta: event => handleDelta(event, assistantMessage, sessionId, operationVersion),
    onFinal: event => handleFinal(event, taskId, assistantMessage, sessionId, operationVersion),
    onError: () => handleStreamError(taskId, assistantMessage, sessionId, operationVersion)
  })
  console.log('[知识查询] SSE 已创建：', { taskId })
}

/** ready 只表示传输通道已建立。 */
function handleReady(event, taskId, sessionId, operationVersion) {
  if (!isCurrentOperation(sessionId, operationVersion) || finalReceived) return
  parseEventData(event, 'ready')
  streamState.value = 'streaming'
  console.log('[知识查询] SSE 已连接：', { taskId })
}

/** progress 更新节点状态，不把节点名拼入答案正文。 */
function handleProgress(event, assistantMessage, sessionId, operationVersion) {
  if (!isCurrentOperation(sessionId, operationVersion) || finalReceived) return
  const eventData = parseEventData(event, 'progress')
  if (!eventData) return
  assistantMessage.progress = {
    status: typeof eventData.status === 'string' ? eventData.status : '',
    doneList: safeStringList(eventData.done_list),
    runningList: safeStringList(eventData.running_list)
  }
}

/** delta 只追加字符串增量，保持服务端到达顺序。 */
function handleDelta(event, assistantMessage, sessionId, operationVersion) {
  if (!isCurrentOperation(sessionId, operationVersion) || finalReceived) return
  const eventData = parseEventData(event, 'delta')
  if (eventData && typeof eventData.delta === 'string' && eventData.delta) {
    assistantMessage.text += eventData.delta
    scrollMessagesToBottom()
  }
}

/** final 统一处理正式与兼容终态，完整答案优先覆盖累计增量。 */
function handleFinal(event, taskId, assistantMessage, sessionId, operationVersion) {
  if (!isCurrentOperation(sessionId, operationVersion) || finalReceived) return
  finalReceived = true
  const eventData = parseEventData(event, 'final') || {}
  const restricted = eventData.result_type === 'restricted'
  const failed = eventData.status === 'failed' || Boolean(eventData.error)
  const finalAnswer = typeof eventData.answer === 'string' ? eventData.answer.trim() : ''
  const finalItemNames = safeStringList(eventData.item_names)
  assistantMessage.requestId = String(taskId)
  assistantMessage.answerType = normalizeAnswerType(eventData.answer_type)
  assistantMessage.feedbackStatus = 0

  if (restricted) {
    assistantMessage.text = '相关知识无访问权限'
    assistantMessage.imageUrls = []
    assistantMessage.status = 'restricted'
    assistantMessage.durationMs = null
    streamState.value = 'restricted'
  } else {
    if (finalAnswer) assistantMessage.text = finalAnswer
    assistantMessage.imageUrls = safeStringList(eventData.image_urls)
    if (failed) {
      const safeError = safeErrorMessage(eventData.error)
      assistantMessage.text = [assistantMessage.text, safeError].filter(Boolean).join('\n\n')
      assistantMessage.status = 'failed'
      streamState.value = 'failed'
    } else {
      assistantMessage.status = 'completed'
      streamState.value = 'completed'
    }
    assistantMessage.durationMs = Math.round(performance.now() - requestStartedAt.value)
  }
  console.log('[知识查询] SSE 终态：', {
    taskId,
    status: streamState.value,
    durationMs: assistantMessage.durationMs
  })
  // 只使用外部服务识别的最后一个知识单元，不从用户问题推测。
  if (finalItemNames.length) {
    latestItemName.value = finalItemNames.at(-1)
    loadFaqRecommendations(latestItemName.value)
  }
  closeEventSource()
  scrollMessagesToBottom()
}

/** 提交正常回答反馈，失败时保留原状态以允许用户再次点击。 */
async function submitFeedback(message, feedbackStatus) {
  if (!message?.requestId || message.feedbackLoading || ![1, 2].includes(feedbackStatus)) return
  const oldFeedbackStatus = message.feedbackStatus
  message.feedbackLoading = true
  console.log('[知识查询] 提交回答反馈：', {
    requestId: message.requestId,
    feedbackStatus
  })
  try {
    const response = await updateKnowledgeQueryFeedback(message.requestId, feedbackStatus)
    message.feedbackStatus = Number(response.data?.feedbackStatus) || feedbackStatus
    console.log('[知识查询] 提交回答反馈完成：', {
      requestId: message.requestId,
      feedbackStatus: message.feedbackStatus,
      code: response.code
    })
    proxy.$modal.msgSuccess('反馈成功')
  } catch (error) {
    message.feedbackStatus = oldFeedbackStatus
    proxy.$modal.msgError(getApiErrorMessage(error) || '反馈失败，请稍后重试')
  } finally {
    message.feedbackLoading = false
  }
}

/** 连接异常保留已收到内容，不自动重提以免生成重复记录。 */
function handleStreamError(taskId, assistantMessage, sessionId, operationVersion) {
  if (!isCurrentOperation(sessionId, operationVersion) || finalReceived) return
  finalReceived = true
  assistantMessage.text = [assistantMessage.text, '响应中断，可重新提问。'].filter(Boolean).join('\n\n')
  assistantMessage.status = 'interrupted'
  assistantMessage.durationMs = Math.round(performance.now() - requestStartedAt.value)
  streamState.value = 'interrupted'
  console.warn('[知识查询] SSE 连接中断：', { taskId, durationMs: assistantMessage.durationMs })
  closeEventSource()
}

/** 清空外部历史成功后才清空本地消息，保证两端状态一致。 */
async function clearCurrentConversation() {
  if (!activeSessionId.value) return
  if (!queryApiConfigured) {
    proxy.$modal.msgWarning('外部知识库服务未配置')
    return
  }
  await proxy.$modal.confirm('确定清空当前会话的全部问答记录吗？此操作无法恢复。')
  const sessionId = activeSessionId.value
  closeEventSource()
  console.log('[知识查询] 清空外部会话历史：', { sessionId })
  try {
    const response = await clearExternalKnowledgeQueryHistory(sessionId)
    if (sessionId !== activeSessionId.value) return
    messages.value = []
    resetFaqContext()
    console.log('[知识查询] 外部会话历史清空完成：', {
      sessionId,
      deletedCount: Number(response.deleted_count) || 0
    })
    proxy.$modal.msgSuccess('当前会话已清空')
  } catch (error) {
    proxy.$modal.msgError('服务端清空失败，请稍后重试')
  }
}

/** 删除平台会话索引，不级联外部问答明细。 */
async function removeSession(session) {
  await proxy.$modal.confirm(`确定删除会话“${session.sessionTitle}”吗？`)
  console.log('[知识查询] 删除会话索引：', { sessionId: session.id })
  await removeKnowledgeQuerySession(session.id)
  if (String(session.id) === activeSessionId.value) createNewConversation()
  proxy.$modal.msgSuccess('会话已删除')
  await loadSessions(true, false)
}

/** 清理当前用户全部平台会话索引。 */
async function clearAllSessions() {
  await proxy.$modal.confirm('确定清理全部历史会话吗？此操作不会清理外部问答明细。')
  closeEventSource()
  console.log('[知识查询] 清理全部会话索引')
  const response = await clearKnowledgeQuerySessions()
  createNewConversation()
  sessionList.value = []
  sessionTotal.value = 0
  console.log('[知识查询] 全部会话索引清理完成：', { deletedCount: Number(response.data) || 0 })
  proxy.$modal.msgSuccess('历史会话已清理')
}

/** 点选 FAQ 后以后端当前数据和鉴权结果为准，不调用外部问答服务。 */
async function selectFaq(faq, sourceType) {
  if (faqSelecting.value || sending.value) return
  const faqId = Number(faq?.id)
  const standardQuestion = String(faq?.standardQuestion || '').trim()
  if (!Number.isSafeInteger(faqId) || faqId <= 0 || !standardQuestion) return
  const retryKey = `${sourceType}:${faqId}:${standardQuestion}`
  // 同一候选失败后的再次点选视为同动作重试，必须复用 request ID 防止重复计数。
  if (!faqRetryContext || faqRetryContext.key !== retryKey) {
    faqRetryContext = { key: retryKey, requestId: crypto.randomUUID() }
  }
  const requestId = faqRetryContext.requestId
  const originalQuestion = question.value
  const operationVersion = conversationVersion
  faqSelecting.value = true
  clearFaqSuggestions()
  console.log('[知识查询] 点选 FAQ：', { apiType: 'answer', faqId })
  try {
    const sessionId = await ensureSession(standardQuestion)
    if (!isCurrentOperation(sessionId, operationVersion)) return
    question.value = ''
    messages.value.push(createMessage('user', standardQuestion, 'completed', 'faq'))
    scrollMessagesToBottom()

    const response = await getFaqAnswer(faqId, requestId)
    if (!isCurrentOperation(sessionId, operationVersion)) return
    const standardAnswer = typeof response.data?.standardAnswer === 'string'
      ? response.data.standardAnswer.trim()
      : ''
    const answerMessage = createMessage(
      'assistant',
      standardAnswer || '该 FAQ 暂无标准答案。',
      'completed',
      'faq'
    )
    messages.value.push(answerMessage)
    faqRetryContext = null
    console.log('[知识查询] FAQ 答案查询完成：', {
      apiType: 'answer',
      faqId,
      status: response.code
    })
  } catch (error) {
    if (operationVersion !== conversationVersion) return
    const errorMessage = getApiErrorMessage(error)
    const restricted = errorMessage.includes('您没有权限查看该知识')
    const invalid = errorMessage.includes('该 FAQ 已失效')
    messages.value.push(createMessage(
      'assistant',
      restricted ? '您没有权限查看该知识' : invalid ? '该 FAQ 已失效，请刷新后重试' : 'FAQ 查询失败，请稍后重试。',
      restricted ? 'restricted' : 'failed',
      'faq'
    ))
    console.warn('[知识查询] FAQ 答案查询未完成：', {
      apiType: 'answer',
      faqId,
      status: restricted ? 'restricted' : invalid ? 'invalid' : 'failed'
    })
    // 失效内容必须刷新原入口，避免用户继续点选已撤回的候选。
    if (invalid && sourceType === 'recommendation') await loadFaqRecommendations(latestItemName.value)
    if (invalid && sourceType === 'suggestion') {
      question.value = originalQuestion
      handleQuestionInput()
    }
  } finally {
    if (operationVersion === conversationVersion) faqSelecting.value = false
    scrollMessagesToBottom()
  }
}

/** 联想打开时优先处理方向键、Enter 和 Esc，其他 Enter 保留普通发送逻辑。 */
function handleQuestionKeydown(event) {
  if (event.isComposing) return
  if (event.key === 'Escape' && (suggestionList.value.length || suggestionLoading.value)) {
    event.preventDefault()
    clearFaqSuggestions()
    return
  }
  if (event.key === 'ArrowDown' && suggestionList.value.length) {
    event.preventDefault()
    activeSuggestionIndex.value = (activeSuggestionIndex.value + 1) % suggestionList.value.length
    return
  }
  if (event.key === 'ArrowUp' && suggestionList.value.length) {
    event.preventDefault()
    activeSuggestionIndex.value = activeSuggestionIndex.value <= 0
      ? suggestionList.value.length - 1
      : activeSuggestionIndex.value - 1
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    const activeFaq = suggestionList.value[activeSuggestionIndex.value]
    if (activeFaq) selectFaq(activeFaq, 'suggestion')
    else sendQuestion()
  }
}

/** 复制完成态纯文本答案，不包含图片地址。 */
async function copyAnswer(answerText) {
  try {
    await navigator.clipboard.writeText(answerText)
    proxy.$modal.msgSuccess('答案已复制')
  } catch (error) {
    proxy.$modal.msgError('复制失败，请手动选择文本复制')
  }
}

/** 关闭连接可被重复调用，主动关闭同时解除发送锁。 */
function closeEventSource(resetSending = true) {
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
  if (resetSending) sending.value = false
}

/** 安全解析单个 SSE 事件，非法事件只记录类型和长度。 */
function parseEventData(event, eventType) {
  try {
    return JSON.parse(event?.data || '{}')
  } catch (error) {
    console.warn('[知识查询] SSE 事件解析失败：', {
      eventType,
      dataLength: String(event?.data || '').length
    })
    return null
  }
}

/** 判断异步回调是否仍属于当前会话操作。 */
function isCurrentOperation(sessionId, operationVersion) {
  return sessionId === activeSessionId.value && operationVersion === conversationVersion
}

/** 重置会话绑定的 FAQ 上下文，同时使在途请求失效。 */
function resetFaqContext() {
  recommendationRequestVersion += 1
  latestItemName.value = ''
  recommendationList.value = []
  faqSelecting.value = false
  faqRetryContext = null
  clearFaqSuggestions()
}

/** 只接受非空字符串数组，避免异常对象进入页面渲染。 */
function safeStringList(candidateList) {
  return Array.isArray(candidateList)
    ? candidateList.filter(item => typeof item === 'string' && item.trim()).map(item => item.trim())
    : []
}

/** 从历史消息中倒序获取最新的外部识别知识单元。 */
function findLatestItemName(historyItems) {
  // 历史顺序由旧到新，故从末尾开始查找可减少无效遍历。
  for (let index = historyItems.length - 1; index >= 0; index -= 1) {
    const itemNames = safeStringList(historyItems[index]?.item_names)
    if (itemNames.length) return itemNames.at(-1)
  }
  return ''
}

/** 兼容若依业务异常和 HTTP 异常中的短错误文案。 */
function getApiErrorMessage(error) {
  const responseMessage = error?.response?.data?.msg
  if (typeof responseMessage === 'string' && responseMessage.trim()) return responseMessage.trim()
  return typeof error?.message === 'string' ? error.message.trim() : ''
}

/** 将外部回答类型规范为四种协议值，异常或缺失值不开放反馈入口。 */
function normalizeAnswerType(answerType) {
  const normalizedType = Number(answerType)
  return Number.isInteger(normalizedType) && normalizedType >= 0 && normalizedType <= 3
    ? normalizedType
    : null
}

/** 外部错误仅展示短文本，不向页面暴露堆栈或结构化异常。 */
function safeErrorMessage(errorMessage) {
  const safeMessage = typeof errorMessage === 'string' ? errorMessage.trim().slice(0, 200) : ''
  return safeMessage || '本次回答失败，可重新提问。'
}

/** 创建页面消息对象，来源字段用于区分外部 AI 与 FAQ 直接答案。 */
function createMessage(role, text, status, source = role === 'assistant' ? 'ai' : 'user') {
  return {
    localId: `message-${Date.now()}-${++messageSequence}`,
    role,
    text,
    imageUrls: [],
    time: formatMessageTime(Date.now() / 1000),
    status,
    source,
    requestId: '',
    answerType: null,
    feedbackStatus: 0,
    feedbackLoading: false,
    durationMs: null,
    progress: { status: '', doneList: [], runningList: [] }
  }
}

/** 将外部历史项转换为页面内部消息，不保留 MongoDB 标识。 */
function createHistoryMessage(historyItem) {
  const message = createMessage(historyItem.role, String(historyItem.text || ''), 'completed')
  message.imageUrls = safeStringList(historyItem.image_urls)
  message.time = formatMessageTime(historyItem.ts)
  // 历史文档缺少请求 ID 时不展示反馈，避免把旧消息错误关联到日志。
  if (historyItem.role === 'assistant') {
    message.requestId = typeof historyItem.request_id === 'string' ? historyItem.request_id.trim() : ''
    message.answerType = normalizeAnswerType(historyItem.answer_type)
  }
  return message
}

/** 获取指定时刻在 Asia/Shanghai 的日期键。 */
function getShanghaiDateKey(date) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date).replaceAll('/', '-')
}

/** 将会话创建时间格式化为列表时间。 */
function formatSessionTime(createTime) {
  const timeText = String(createTime || '')
  return timeText.length >= 16 ? timeText.slice(11, 16) : '--:--'
}

/** 将 Unix 秒时间格式化为上海时区时分。 */
function formatMessageTime(timestamp) {
  const numericTimestamp = Number(timestamp)
  if (!Number.isFinite(numericTimestamp)) return '--:--'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(numericTimestamp * 1000))
}

/** 消息更新后滚动到末尾，不在增量回调中查询全页 DOM。 */
async function scrollMessagesToBottom() {
  await nextTick()
  const panel = messagePanelRef.value
  if (panel) panel.scrollTop = panel.scrollHeight
}

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  clearFaqSuggestions()
  closeEventSource()
})

loadSessions(true)
</script>

<style scoped lang="scss">
.knowledge-query-page {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page, #f2f3f5);
}

.page-heading,
.conversation-header,
.conversation-title,
.history-header,
.history-header strong,
.history-footer,
.composer-tip {
  display: flex;
  align-items: center;
}

.page-heading {
  justify-content: space-between;
  margin-bottom: 18px;

  h2 { margin: 0; color: var(--el-text-color-primary); font-size: 24px; }
  p { margin: 7px 0 0; color: var(--el-text-color-secondary); font-size: 14px; }
}

.service-alert { margin-bottom: 16px; }

.query-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 16px;
  height: calc(100vh - 190px);
  min-height: 590px;
}

.conversation-card,
.history-card {
  height: 100%;
  border-radius: 8px;

  :deep(.el-card__body) { min-height: 0; }
}

.conversation-card {
  :deep(.el-card__body) {
    display: grid;
    grid-template-rows: minmax(0, 1fr) auto auto auto;
    height: calc(100% - 61px);
    padding: 0;
  }
}

.conversation-header { justify-content: space-between; }
.conversation-title { gap: 10px; }
.online-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--el-color-success); }

.message-panel {
  overflow-y: auto;
  padding: 28px 28px 18px;
  background: var(--el-fill-color-lighter);
  scroll-behavior: smooth;
}

.empty-conversation {
  display: flex;
  height: 100%;
  min-height: 260px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--el-text-color-secondary);
  text-align: center;

  .el-icon { color: var(--el-color-primary); font-size: 48px; }
  h3 { margin: 16px 0 8px; color: var(--el-text-color-primary); }
  p { margin: 0; }
}

.message-row { display: flex; gap: 12px; margin-bottom: 24px; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { box-sizing: border-box; padding-right: 38px; }
.message-row.assistant :deep(.assistant-answer) { min-width: 0; flex: 1; }
.user-message { min-width: 0; max-width: calc(74% - 50px); }
.user-text {
  padding: 14px 18px;
  border-radius: 10px 10px 2px 10px;
  background: var(--el-color-primary);
  color: #fff;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.user-message time { display: block; margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; text-align: right; }
.user-avatar,
.assistant-avatar {
  display: flex;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}
.user-avatar { border: 1px solid var(--el-color-primary-light-5); border-radius: 50%; background: var(--el-color-primary-light-8); color: var(--el-color-primary); }
.assistant-avatar { background: var(--el-color-primary); color: #fff; font-size: 20px; }

.faq-recommendations {
  padding: 12px 20px 4px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.faq-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
  color: var(--el-text-color-regular);
  font-size: 13px;

  span { display: inline-flex; align-items: center; gap: 5px; font-weight: 600; }
  .el-icon { color: var(--el-color-warning); }
  small { color: var(--el-text-color-secondary); }
}

.faq-chip-list { display: flex; flex-wrap: wrap; gap: 8px; }
.faq-chip,
.faq-suggestion-item {
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: pointer;
}
.faq-chip {
  max-width: 100%;
  padding: 6px 11px;
  border-radius: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover,
  &:focus-visible { border-color: var(--el-color-primary); color: var(--el-color-primary); outline: none; }
  &:disabled { cursor: not-allowed; opacity: 0.6; }
}

.question-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 16px 20px 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);

  :deep(.el-textarea__inner) { min-height: 72px !important; padding-right: 58px; }
}
.question-input-wrap { position: relative; min-width: 0; }
.faq-suggestion-panel {
  position: absolute;
  z-index: 20;
  right: 0;
  bottom: calc(100% + 6px);
  left: 0;
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  background: var(--el-bg-color-overlay);
  box-shadow: var(--el-box-shadow-light);
}
.faq-suggestion-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 15px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.faq-suggestion-item {
  display: block;
  width: 100%;
  padding: 10px 13px;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-align: left;

  &:last-child { border-bottom: 0; }
  &:hover,
  &:focus-visible,
  &.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); outline: none; }
}
.send-button { align-self: end; min-width: 92px; height: 40px; }
.composer-tip { gap: 6px; padding: 0 20px 14px; color: var(--el-text-color-secondary); font-size: 12px; }

.history-card {
  :deep(.el-card__body) { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; height: calc(100% - 61px); padding: 0; }
}
.history-header { justify-content: space-between; }
.history-header strong { gap: 6px; }
.history-search { padding: 14px 14px 8px; }
.history-list { overflow-y: auto; padding: 0 10px 10px; }
.group-title { padding: 12px 6px 7px; color: var(--el-text-color-secondary); font-size: 13px; font-weight: 600; }
.session-item {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 8px;
  padding: 11px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover { background: var(--el-fill-color-light); }
  &.active {
    background: var(--primary-bg, var(--el-color-primary-light-9));
    color: var(--el-color-primary);
  }
}
.session-main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 5px; }
.session-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-main time { color: var(--el-text-color-placeholder); font-size: 12px; }
.history-loading { padding: 18px; color: var(--el-text-color-secondary); text-align: center; }
.history-footer { justify-content: space-between; padding: 12px 14px; border-top: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-secondary); font-size: 13px; }

@media (max-width: 1100px) {
  .query-layout { grid-template-columns: minmax(0, 1fr) 280px; }
}

@media (max-width: 820px) {
  .query-layout { display: flex; height: auto; flex-direction: column; }
  .conversation-card { height: 650px; }
  .history-card { height: 480px; }
  .page-heading p { display: none; }
}
</style>
