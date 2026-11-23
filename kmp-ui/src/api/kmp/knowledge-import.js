import request from '@/utils/request'

/**
 * 分页查询知识导入任务。
 *
 * @param {Object} query 分页参数
 * @returns {Promise} 任务分页结果
 */
export function listKnowledgeImportTask(query) {
  return request({
    url: '/kmp/knowledgeImportTask/list',
    method: 'get',
    params: query
  })
}

/**
 * 在文件对外上传前初始化平台任务。
 *
 * @param {Object} submitForm 文件名和知识组 ID
 * @returns {Promise} 平台任务 ID
 */
export function initKnowledgeImportTask(submitForm) {
  return request({
    url: '/kmp/knowledgeImportTask/init',
    method: 'post',
    data: submitForm,
    headers: { repeatSubmit: false }
  })
}

/**
 * 将外部服务返回的任务编号绑定到原平台任务。
 *
 * @param {number|string} id 平台任务 ID
 * @param {Object} submitForm 外部任务编号
 * @returns {Promise} 绑定后的平台任务
 */
export function bindExternalKnowledgeImportTask(id, submitForm) {
  return request({
    url: `/kmp/knowledgeImportTask/${id}/external-task`,
    method: 'put',
    data: submitForm,
    headers: { repeatSubmit: false }
  })
}

/**
 * 逻辑移除未绑定待上传任务或导入失败任务。
 *
 * @param {number|string} id 平台任务 ID
 * @returns {Promise} 移除结果
 */
export function removeKnowledgeImportTask(id) {
  return request({
    url: `/kmp/knowledgeImportTask/${id}`,
    method: 'delete'
  })
}
