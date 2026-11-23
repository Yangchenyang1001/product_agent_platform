import request from '@/utils/request'

/**
 * 分页查询知识组。
 *
 * @param {Object} query 查询与分页参数
 * @returns {Promise} 分页查询结果
 */
export function listKnowledgeGroup(query) {
  return request({
    url: '/kmp/knowledgeGroup/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询知识组详情。
 *
 * @param {number|string} id 知识组 ID
 * @returns {Promise} 详情查询结果
 */
export function getKnowledgeGroup(id) {
  return request({
    url: '/kmp/knowledgeGroup/' + id,
    method: 'get'
  })
}

/**
 * 新增知识组。
 *
 * @param {Object} submitForm 待新增的业务字段
 * @returns {Promise} 新增结果
 */
export function addKnowledgeGroup(submitForm) {
  return request({
    url: '/kmp/knowledgeGroup',
    method: 'post',
    data: submitForm
  })
}

/**
 * 修改知识组。
 *
 * @param {Object} submitForm 待修改的业务字段
 * @returns {Promise} 修改结果
 */
export function updateKnowledgeGroup(submitForm) {
  return request({
    url: '/kmp/knowledgeGroup',
    method: 'put',
    data: submitForm
  })
}

/**
 * 删除知识组。
 *
 * @param {number|string} id 知识组 ID
 * @returns {Promise} 删除结果
 */
export function deleteKnowledgeGroup(id) {
  return request({
    url: '/kmp/knowledgeGroup/' + id,
    method: 'delete'
  })
}


/** 查询当前用户可用于导入的知识组选项。 */
export function listKnowledgeGroupOptions() {
  return request({
    url: '/kmp/knowledgeGroup/groupOptions',
    method: 'get'
  })
}

