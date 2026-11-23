import axios from 'axios'

const importApiBase = (import.meta.env.VITE_APP_KNOWLEDGE_IMPORT_API_BASE || '').replace(/\/$/, '')

/** 判断部署环境是否已配置外部知识库服务地址。 */
export function isKnowledgeImportApiConfigured() {
  return Boolean(importApiBase)
}

/**
 * 将单个文件直传外部知识库，不携带若依令牌或 Cookie。
 *
 * @param {File} file 待上传文件
 * @param {Object} requestOptions 取消信号和上传进度回调
 * @returns {Promise<Object>} 外部服务受理结果
 */
export async function uploadKnowledgeFile(file, requestOptions = {}) {
  if (!isKnowledgeImportApiConfigured()) {
    throw new Error('外部知识库服务未配置')
  }
  const formData = new FormData()
  formData.append('file', file, file.name)
  const response = await axios.post(`${importApiBase}/upload`, formData, {
    // 覆盖 request.js 设置的全局 JSON 请求头，避免 Axios 将 FormData 序列化成 JSON。
    headers: { 'Content-Type': 'multipart/form-data' },
    signal: requestOptions.signal,
    onUploadProgress: requestOptions.onUploadProgress,
    timeout: 120000,
    withCredentials: false
  })
  return response.data
}
