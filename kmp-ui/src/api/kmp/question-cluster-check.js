import request from '@/utils/request'

/** 创建待检测问题的异步归纳任务。 */
export function createQuestionClusterCheckTask() {
  return request({
    url: '/kmp/questionCluster/checkTasks',
    method: 'post'
  })
}
