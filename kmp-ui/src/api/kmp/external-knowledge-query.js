import axios from 'axios'

const queryApiBase = String(import.meta.env.VITE_APP_KNOWLEDGE_QUERY_API_BASE || '')
  .trim()
  .replace(/\/+$/, '')

const externalRequest = axios.create({
  baseURL: queryApiBase || undefined,
  timeout: 10000,
  withCredentials: false,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

/** 判断外部知识查询服务是否已经配置。 */
export function isKnowledgeQueryApiConfigured() {
  return Boolean(queryApiBase)
}

/** 提交流式知识查询任务。 */
export async function submitExternalKnowledgeQuery(submitForm) {
  const response = await externalRequest.post('/query', submitForm)
  return response.data
}

/** 建立知识查询 SSE 连接并绑定正式事件。 */
export function openKnowledgeQueryStream(taskId, handlers) {
  const streamUrl = `${queryApiBase}/stream/${encodeURIComponent(taskId)}`
  const eventSource = new EventSource(streamUrl, { withCredentials: false })
  eventSource.addEventListener('ready', handlers.onReady)
  eventSource.addEventListener('progress', handlers.onProgress)
  eventSource.addEventListener('delta', handlers.onDelta)
  eventSource.addEventListener('final', handlers.onFinal)
  // 兼容仍沿用示例页事件名的联调环境，终态逻辑仍统一由 onFinal 处理。
  eventSource.addEventListener('final_answer', handlers.onFinal)
  eventSource.onerror = handlers.onError
  return eventSource
}

/** 查询指定会话的外部问答历史。 */
export async function getExternalKnowledgeQueryHistory(sessionId, limit = 50) {
  const response = await externalRequest.get(`/history/${encodeURIComponent(sessionId)}`, {
    params: { limit }
  })
  return response.data
}

/** 清空指定会话的外部问答历史。 */
export async function clearExternalKnowledgeQueryHistory(sessionId) {
  const response = await externalRequest.delete(`/history/${encodeURIComponent(sessionId)}`)
  return response.data
}
