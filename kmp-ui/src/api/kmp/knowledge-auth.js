import request from '@/utils/request'

/**
 * 分页查询知识组权限配置概览。
 *
 * @param {Object} query 查询与分页参数
 * @returns {Promise} 分页查询结果
 */
export function listKnowledgeAuth(query) {
  return request({
    url: '/kmp/knowledgeAuth/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询指定知识组的完整权限配置。
 *
 * @param {number|string} knowledgeGroupId 知识组 ID
 * @returns {Promise} 权限配置详情
 */
export function getKnowledgeAuth(knowledgeGroupId) {
  return request({
    url: '/kmp/knowledgeAuth/' + knowledgeGroupId,
    method: 'get'
  })
}

/**
 * 全量替换指定知识组的权限配置。
 *
 * @param {number|string} knowledgeGroupId 知识组 ID
 * @param {Object} submitForm 完整权限快照
 * @returns {Promise} 保存结果
 */
export function saveKnowledgeAuth(knowledgeGroupId, submitForm) {
  return request({
    url: '/kmp/knowledgeAuth/' + knowledgeGroupId,
    method: 'put',
    data: submitForm
  })
}
