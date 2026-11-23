import request from '@/utils/request'

/** 查询知识看板概览与 Token 图表数据。 */
export async function getDashboardOverview(params) {
  console.log('[知识看板] 查询概览，请求参数：', { range: params.range })
  const response = await request({
    url: '/kmp/dashboard/overview',
    method: 'get',
    params
  })
  console.log('[知识看板] 查询概览完成：', {
    range: params.range,
    code: response.code,
    incompleteMetricCount: response.data?.incompleteMetricNames?.length || 0
  })
  return response
}

/** 按所选时间范围查询热门问题排行榜。 */
export async function getHotQuestions(params) {
  console.log('[知识看板] 查询热门问题排行榜，请求参数：', { range: params.range })
  const response = await request({
    url: '/kmp/dashboard/hotQuestions',
    method: 'get',
    params
  })
  console.log('[知识看板] 查询热门问题排行榜完成：', {
    range: params.range,
    code: response.code,
    count: response.data?.length || 0
  })
  return response
}

/** 按所选时间范围查询热门知识单元排行榜。 */
export async function getHotKnowledgeUnits(params) {
  console.log('[知识看板] 查询热门知识单元排行榜，请求参数：', { range: params.range })
  const response = await request({
    url: '/kmp/dashboard/hotKnowledgeUnits',
    method: 'get',
    params
  })
  console.log('[知识看板] 查询热门知识单元排行榜完成：', {
    range: params.range,
    code: response.code,
    count: response.data?.length || 0
  })
  return response
}
