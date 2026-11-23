import request from '@/utils/request'

/** 分页查询问题簇。 */
export function listQuestionCluster(query) {
  return request({
    url: '/kmp/questionCluster/list',
    method: 'get',
    params: query
  })
}

/** 分页查询存在无帮助或资料缺失拒答的问题簇。 */
export function listKnowledgeMissing(queryParams) {
  return request({
    url: '/kmp/questionCluster/missing/list',
    method: 'get',
    params: queryParams
  })
}

/** 查询问题簇详情。 */
export function getQuestionCluster(id) {
  return request({
    url: `/kmp/questionCluster/${id}`,
    method: 'get'
  })
}

/** 手工新增问题簇。 */
export function addQuestionCluster(submitForm) {
  return request({
    url: '/kmp/questionCluster',
    method: 'post',
    data: submitForm
  })
}

/** 修改问题簇可编辑字段。 */
export function updateQuestionCluster(submitForm) {
  return request({
    url: '/kmp/questionCluster',
    method: 'put',
    data: submitForm
  })
}

/** 逻辑删除未发布问题簇。 */
export function deleteQuestionCluster(id) {
  return request({
    url: `/kmp/questionCluster/${id}`,
    method: 'delete'
  })
}

/** 重试同步问题簇向量。 */
export function retryQuestionClusterVector(id) {
  return request({
    url: `/kmp/questionCluster/${id}/vector/retry`,
    method: 'post'
  })
}

/** 生成仅回填页面的参考答案。 */
export function generateReferenceAnswer(id) {
  return request({
    url: `/kmp/questionCluster/${id}/reference-answer`,
    method: 'post'
  })
}

/** 将沉淀中或待发布问题簇发布为 FAQ。 */
export function publishQuestionCluster(id) {
  return request({
    url: `/kmp/questionCluster/${id}/publish`,
    method: 'post'
  })
}

/** 取消已发布 FAQ。 */
export function unpublishQuestionCluster(id) {
  return request({
    url: `/kmp/questionCluster/${id}/unpublish`,
    method: 'post'
  })
}
