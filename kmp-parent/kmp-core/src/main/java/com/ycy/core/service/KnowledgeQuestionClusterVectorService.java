package com.ycy.core.service;

import com.ycy.core.entity.KnowledgeQuestionCluster;

public interface KnowledgeQuestionClusterVectorService {
    /** 按问题簇主键幂等写入向量。 */
    void saveQuestionClusterVector(KnowledgeQuestionCluster cluster);

    /** 按问题簇主键幂等删除向量。 */
    void deleteQuestionClusterVector(Long questionClusterId);
}
