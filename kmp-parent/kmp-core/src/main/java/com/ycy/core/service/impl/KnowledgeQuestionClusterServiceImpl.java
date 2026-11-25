package com.ycy.core.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ycy.core.client.FaqSearchClient;
import com.ycy.core.constant.QuestionClusterConstants;
import com.ycy.core.dto.KnowledgeFaqModels;
import com.ycy.core.entity.KnowledgeGroup;
import com.ycy.core.entity.KnowledgeQuestionCluster;
import com.ycy.core.entity.KnowledgeUnit;
import com.ycy.core.mapper.KnowledgeQuestionClusterMapper;
import com.ycy.core.service.KnowledgeGroupService;
import com.ycy.core.service.KnowledgeQuestionClusterService;
import com.ycy.core.service.KnowledgeQuestionClusterVectorService;
import com.ycy.core.service.KnowledgeUnitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 问题簇表 服务实现类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
@Service
public class KnowledgeQuestionClusterServiceImpl extends ServiceImpl<KnowledgeQuestionClusterMapper, KnowledgeQuestionCluster> implements KnowledgeQuestionClusterService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeQuestionClusterServiceImpl.class);

    @Autowired
    private KnowledgeQuestionClusterVectorService knowledgeQuestionClusterVectorService;


    /**
     * {@inheritDoc}
     */
    @Override
    public IPage<KnowledgeQuestionCluster> pageQuestionClusters(Page<KnowledgeQuestionCluster> page,
                                                                KnowledgeQuestionCluster query) {
        IPage<KnowledgeQuestionCluster> result = baseMapper.selectQuestionClusterPage(page, query);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createQuestionCluster(KnowledgeQuestionCluster cluster, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // 所有状态、统计和审计字段均由服务端初始化，忽略客户端伪造值。
        cluster.setId(null);
        cluster.setStatus(QuestionClusterConstants.STATUS_ACCUMULATING);
        cluster.setAssociationCount(0L);
        cluster.setFaqClickCount(0L);
        cluster.setExposureCount(0L);
        cluster.setHelpfulCount(0L);
        cluster.setUnhelpfulCount(0L);
        cluster.setRefusalCount(0L);
        cluster.setRecentQuestions(Collections.emptyList());
        cluster.setCreateUserId(userId);
        cluster.setCreateTime(now);
        cluster.setUpdateUserId(null);
        cluster.setUpdateTime(null);
        cluster.setIsDeleted(0);
        save(cluster);

        boolean vectorSynced = true;
        try {
            knowledgeQuestionClusterVectorService.saveQuestionClusterVector(cluster);
        } catch (RuntimeException exception) {
            vectorSynced = false;
            log.warn("问题簇已保存但向量同步失败");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", cluster.getId());
        result.put("vectorSynced", vectorSynced);
        return result;
    }

    @Autowired
    private KnowledgeUnitService knowledgeUnitService;

    @Autowired
    private KnowledgeGroupService knowledgeGroupService;


    /**
     * {@inheritDoc}
     */
    @Override
    public KnowledgeQuestionCluster getQuestionClusterDetail(Long id) {
        KnowledgeQuestionCluster cluster = this.getById(id);
        KnowledgeUnit knowledgeUnit = knowledgeUnitService.getById(cluster.getKnowledgeUnitId());
        KnowledgeGroup knowledgeGroup = knowledgeGroupService.getById(cluster.getKnowledgeGroupId());
        cluster.setKnowledgeUnitName(knowledgeUnit == null ? null : knowledgeUnit.getKnowledgeUnitName());
        cluster.setKnowledgeGroupName(knowledgeGroup == null ? null : knowledgeGroup.getKnowledgeGroupName());
        if (cluster.getRecentQuestions() == null) {
            cluster.setRecentQuestions(Collections.emptyList());
        }
        return cluster;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionCluster(KnowledgeQuestionCluster input, Long userId) {

        // 设置可信字段，防止客户端伪造
        KnowledgeQuestionCluster cluster = new KnowledgeQuestionCluster();
        cluster.setId(input.getId());
        cluster.setStandardQuestion(input.getStandardQuestion());
        cluster.setStandardAnswer(input.getStandardAnswer());
        cluster.setKnowledgeUnitId(input.getKnowledgeUnitId());
        cluster.setKnowledgeGroupId(input.getKnowledgeGroupId());
        // 更新向量
        knowledgeQuestionClusterVectorService.saveQuestionClusterVector(cluster);

        // 已发布问题变化时，更新ES。
        KnowledgeQuestionCluster current = this.getById(input.getId());
        if (Integer.valueOf(QuestionClusterConstants.STATUS_PUBLISHED).equals(current.getStatus())) {
            // 更新ES
            faqSearchClient.saveFaq(current);
        }

        // 更新数据库
        lambdaUpdate()
                .eq(KnowledgeQuestionCluster::getId, current.getId())
                .eq(KnowledgeQuestionCluster::getStatus, current.getStatus())
                .set(KnowledgeQuestionCluster::getStandardQuestion, input.getStandardQuestion())
                .set(KnowledgeQuestionCluster::getStandardAnswer, input.getStandardAnswer())
                .set(KnowledgeQuestionCluster::getKnowledgeUnitId, input.getKnowledgeUnitId())
                .set(KnowledgeQuestionCluster::getKnowledgeGroupId, input.getKnowledgeGroupId())
                .set(KnowledgeQuestionCluster::getUpdateUserId, userId)
                .set(KnowledgeQuestionCluster::getUpdateTime, LocalDateTime.now())
                .update();

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeQuestionCluster(Long id, Long userId) {
        knowledgeQuestionClusterVectorService.deleteQuestionClusterVector(id);
        this.lambdaUpdate()
                .eq(KnowledgeQuestionCluster::getId, id)
                .set(KnowledgeQuestionCluster::getUpdateUserId, userId)
                .set(KnowledgeQuestionCluster::getUpdateTime, LocalDateTime.now())
                .set(KnowledgeQuestionCluster::getIsDeleted, 1)
                .update();
    }

    @Autowired
    private FaqSearchClient faqSearchClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishQuestionCluster(Long id, Long userId) {
        //获取当前问题簇数据记录
        KnowledgeQuestionCluster cluster = this.getById(id);
        Integer oldStatus = cluster.getStatus();

        if (!Integer.valueOf(QuestionClusterConstants.STATUS_PUBLISHED).equals(oldStatus)) {
            this.lambdaUpdate()
                    .eq(KnowledgeQuestionCluster::getId, id)
                    .eq(KnowledgeQuestionCluster::getStatus, oldStatus)
                    .set(KnowledgeQuestionCluster::getStatus, QuestionClusterConstants.STATUS_PUBLISHED)
                    .set(KnowledgeQuestionCluster::getUpdateUserId, userId)
                    .set(KnowledgeQuestionCluster::getUpdateTime, LocalDateTime.now())
                    .update();

            //再保存es，如果出错，MySQL可以回滚
            faqSearchClient.saveFaq(cluster);
        }


    }

    @Override
    public void unpublishQuestionCluster(Long id, Long userId) {

        KnowledgeQuestionCluster knowledgeQuestionCluster = this.getById(id);
        Integer oldStatus = knowledgeQuestionCluster.getStatus();
        if (Integer.valueOf(QuestionClusterConstants.STATUS_PUBLISHED).equals(oldStatus)) {
            this.lambdaUpdate()
                    .eq(KnowledgeQuestionCluster::getId, id)
                    .eq(KnowledgeQuestionCluster::getStatus, oldStatus)
                    .set(KnowledgeQuestionCluster::getStatus, QuestionClusterConstants.STATUS_PENDING)
                    .set(KnowledgeQuestionCluster::getUpdateUserId, userId)
                    .set(KnowledgeQuestionCluster::getUpdateTime, LocalDateTime.now())
                    .update();

            //再保存es，如果出错，MySQL可以回滚
            faqSearchClient.deleteFaq(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<KnowledgeFaqModels.KnowledgeFaqItem> listRecommendedFaqs(String itemName) {
        List<KnowledgeQuestionCluster> clusters = baseMapper.selectRecommendedFaqs(itemName, QuestionClusterConstants.FAQ_RECOMMENDATION_LIMIT);
        if (clusters == null || clusters.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> clusterIds = clusters.stream().map(KnowledgeQuestionCluster::getId).collect(Collectors.toList());
        //累加曝光数
        updateExposureCount(clusterIds);

        List<KnowledgeFaqModels.KnowledgeFaqItem> list = clusters.stream().map(cluster -> toFaqItem(cluster)).toList();
        return list;
    }

    @Override
    public List<KnowledgeFaqModels.KnowledgeFaqItem> listSuggestedFaqs(String keyword) {

        //小于5的字符不触发联想
        if (QuestionClusterConstants.FAQ_SUGGESTION_MIN_LENGTH > keyword.length()) {
            return Collections.emptyList();
        }
        // ES 全文匹配，返回按相关度排序的候选 ID。
        List<Long> candidateIds = faqSearchClient.searchFaqIds(
                keyword,
                QuestionClusterConstants.FAQ_SUGGESTION_LIMIT,
                QuestionClusterConstants.FAQ_SUGGESTION_MIN_SCORE
        );
        // 空候选直接返回。
        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeQuestionCluster> validClusters = this.lambdaQuery()
                .select(KnowledgeQuestionCluster::getId, KnowledgeQuestionCluster::getStandardQuestion)
                .in(KnowledgeQuestionCluster::getId, candidateIds)
                .eq(KnowledgeQuestionCluster::getStatus, QuestionClusterConstants.STATUS_PUBLISHED)
                .list();

        return validClusters.stream().map(cluster -> toFaqItem(cluster)).collect(Collectors.toList());
    }


    private static KnowledgeFaqModels.KnowledgeFaqItem toFaqItem(KnowledgeQuestionCluster cluster) {
        KnowledgeFaqModels.KnowledgeFaqItem item = new KnowledgeFaqModels.KnowledgeFaqItem();
        item.setId(cluster.getId());
        item.setStandardQuestion(cluster.getStandardQuestion());
        return item;
    }


    @Override
    public KnowledgeFaqModels.KnowledgeFaqAnswer answerQuestionCluster(Long id, KnowledgeFaqModels.KnowledgeFaqAnswerRequest request, Long userId) {

        KnowledgeQuestionCluster cluster = this.lambdaQuery()
                .eq(KnowledgeQuestionCluster::getId, id)
                .eq(KnowledgeQuestionCluster::getStatus, QuestionClusterConstants.STATUS_PUBLISHED)
                .one();
        if (cluster == null) {
            return null;
        }
        KnowledgeFaqModels.KnowledgeFaqAnswer answer = new KnowledgeFaqModels.KnowledgeFaqAnswer();
        answer.setId(cluster.getId());
        answer.setStandardQuestion(cluster.getStandardQuestion());
        answer.setStandardAnswer(cluster.getStandardAnswer());

        //控制点击数增加
        updateFaqClickCount(List.of(id));

        return answer;
    }

    private boolean updateExposureCount(List<Long> ids) {
        try {
            return this.lambdaUpdate()
                    .in(KnowledgeQuestionCluster::getId, ids)
                    .setSql(" exposure_count = COALESCE(exposure_count, 0) + 1 ")
                    .update();
        } catch (RuntimeException exception) {
            log.error("FAQ曝光数计数失败，ids={}, error={}", ids, exception);
            return false;
        }
    }

    private boolean updateFaqClickCount(List<Long> ids) {
        try {
            return this.lambdaUpdate()
                    .in(KnowledgeQuestionCluster::getId, ids)
                    .setSql(" faq_click_count = COALESCE(faq_click_count, 0) + 1 ")
                    .update();
        } catch (Exception e) {
            log.error("FAQ点击数计数失败，ids={}, error={}", ids, e);
            return false;
        }
    }


}
