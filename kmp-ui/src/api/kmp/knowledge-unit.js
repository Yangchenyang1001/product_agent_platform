import request from '@/utils/request'

/** 分页查询知识单元。 */
export function listKnowledgeUnit(query) {
  return request({
    url: '/kmp/knowledgeUnit/list',
    method: 'get',
    params: query
  })
}

/** 查询知识单元基础详情。 */
export function getKnowledgeUnit(id) {
  return request({
    url: `/kmp/knowledgeUnit/${id}`,
    method: 'get'
  })
}

/** 修改知识单元名称和所属知识组。 */
export function updateKnowledgeUnit(submitForm) {
  return request({
    url: '/kmp/knowledgeUnit',
    method: 'put',
    data: submitForm
  })
}

/** 分页查询知识单元切片。 */
export function listKnowledgeChunks(id, query) {
  return request({
    url: `/kmp/knowledgeUnit/${id}/chunks`,
    method: 'get',
    params: query
  })
}

/** 新增单个知识切片。 */
export function addKnowledgeChunk(id, submitForm) {
  return request({
    url: `/kmp/knowledgeUnit/${id}/chunks`,
    method: 'post',
    data: submitForm,
    headers: { repeatSubmit: false }
  })
}

/** 完整替换单个知识切片的顺序和可编辑内容。 */
export function updateKnowledgeChunk(id, chunkId, submitForm) {
  return request({
    url: `/kmp/knowledgeUnit/${id}/chunks/${encodeURIComponent(String(chunkId))}`,
    method: 'put',
    data: submitForm,
    headers: { repeatSubmit: false }
  })
}

/** 删除单个知识切片。 */
export function deleteKnowledgeChunk(id, chunkId) {
  return request({
    url: `/kmp/knowledgeUnit/${id}/chunks/${encodeURIComponent(String(chunkId))}`,
    method: 'delete'
  })
}

/** 逻辑删除知识单元平台元数据。 */
export function deleteKnowledgeUnit(id) {
  return request({
    url: `/kmp/knowledgeUnit/${id}`,
    method: 'delete'
  })
}

/** 使用文件 SHA-256 摘要检查是否已经导入。 */
export function checkKnowledgeUnitDuplicate(fileSha256) {
  return request({
    url: '/kmp/knowledgeImportTask/duplicate-check',
    method: 'get',
    params: { fileSha256 }
  })
}
