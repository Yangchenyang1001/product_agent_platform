package com.ycy.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.core.entity.KnowledgeQuestionCluster;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 问题簇表 Mapper 接口
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
public interface KnowledgeQuestionClusterMapper extends BaseMapper<KnowledgeQuestionCluster> {

    IPage<KnowledgeQuestionCluster> selectQuestionClusterPage(Page<KnowledgeQuestionCluster> page,@Param("query")  KnowledgeQuestionCluster query);

    List<KnowledgeQuestionCluster> selectRecommendedFaqs(@Param("itemName")String itemName,@Param("limit") int faqRecommendationLimit);
}
