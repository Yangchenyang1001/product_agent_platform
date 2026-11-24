package com.ycy.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ycy.core.entity.KnowledgeQuestionCluster;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <p>
 * 问题簇表 服务类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
public interface KnowledgeQuestionClusterService extends IService<KnowledgeQuestionCluster> {

    IPage<KnowledgeQuestionCluster> pageQuestionClusters(Page<KnowledgeQuestionCluster> page, @Param("query")KnowledgeQuestionCluster query);

    Map<String, Object> createQuestionCluster(KnowledgeQuestionCluster cluster, Long userId);

    KnowledgeQuestionCluster getQuestionClusterDetail(Long id);

    void updateQuestionCluster(KnowledgeQuestionCluster cluster, Long userId);

    void removeQuestionCluster(Long id, Long userId);
}
