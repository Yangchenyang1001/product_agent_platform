import request from '@/utils/request'

/** 分页查询当前用户的知识查询会话。 */
export function listKnowledgeQuerySessions(queryParams) {
  return request({
    url: '/kmp/knowledgeQuerySession/list',
    method: 'get',
    params: queryParams
  })
}

/** 使用首次问题标题创建知识查询会话。 */
export function createKnowledgeQuerySession(submitForm) {
  return request({
    url: '/kmp/knowledgeQuerySession',
    method: 'post',
    data: submitForm
  })
}

/** 删除当前用户的一条知识查询会话索引。 */
export function removeKnowledgeQuerySession(id) {
  return request({
    url: `/kmp/knowledgeQuerySession/${id}`,
    method: 'delete'
  })
}

/** 清理当前用户的全部知识查询会话索引。 */
export function clearKnowledgeQuerySessions() {
  return request({
    url: '/kmp/knowledgeQuerySession/all',
    method: 'delete'
  })
}

/** 按当前会话最新知识单元查询热门 FAQ。 */
export function listRecommendedFaqs(itemName) {
  return request({
    url: '/kmp/questionCluster/faq/recommendations',
    method: 'get',
    params: { itemName }
  })
}

/** 根据用户输入查询 FAQ 联想。 */
export function listSuggestedFaqs(keyword) {
  return request({
    url: '/kmp/questionCluster/faq/suggestions',
    method: 'get',
    params: { keyword }
  })
}

/** 点选 FAQ 后携带幂等请求 ID，由后端鉴权并返回标准答案。 */
export function getFaqAnswer(id, requestId) {
  return request({
    url: `/kmp/questionCluster/faq/${id}/answer`,
    method: 'post',
    data: { requestId },
    // 同一点击失败重试必须复用请求 ID，因此不使用客户端短时重复提交拦截。
    headers: { repeatSubmit: false }
  })
}

/** 提交当前用户对正常回答的有帮助或无帮助反馈。 */
export function updateKnowledgeQueryFeedback(requestId, feedbackStatus) {
  return request({
    url: `/kmp/knowledgeQuery/log/${encodeURIComponent(requestId)}/feedback`,
    method: 'put',
    data: { feedbackStatus },
    // 日志异步落库失败后允许用户用同一反馈值立即重试。
    headers: { repeatSubmit: false }
  })
}
