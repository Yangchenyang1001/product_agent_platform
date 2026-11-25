package com.ycy.core.client;

import com.ycy.common.exception.ServiceException;
import com.ycy.core.config.KmpConfig;
import com.ycy.core.entity.KnowledgeQuestionCluster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FaqSearchClient {
    @Autowired
    @Qualifier("faqSearchRestClient")
    private RestClient restClient;

    @Autowired
    private KmpConfig kmpConfig;

    /**
     * 保存FAQ 支持保存和更新覆盖
     *
     * @param cluster
     */
    public void saveFaq(KnowledgeQuestionCluster cluster) {
        log.info("Saving FAQ: {}", cluster.getStandardQuestion());

        ResponseEntity<Void> response = restClient.put()
                .uri(
                        "/{indexName}/_doc/{questionClusterId}",
                        kmpConfig.getFaqSearchIndexName(),
                        cluster.getId()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(cluster)
                .retrieve()
                .toBodilessEntity();


        int statusCode = response.getStatusCode().value();
        if (statusCode == 201) {
            log.debug("FAQ 文档新建成功，questionClusterId={}", cluster.getId());
        } else if (statusCode == 200) {
            log.debug("FAQ 文档覆盖更新成功，questionClusterId={}", cluster.getId());
        } else {
            log.warn("FAQ 文档写入返回非预期状态码，questionClusterId={}，status={}", cluster.getId(), statusCode);
            throw new ServiceException("FAQ 搜索服务暂时不可用");
        }
    }

    /**
     * 删除FAQ
     *
     * @param questionClusterId
     */


    public void deleteFaq(Long questionClusterId) {
        log.info("Deleting FAQ: {}", questionClusterId);
        ResponseEntity<Void> response = restClient.delete()
                .uri("/{indexName}/_doc/{questionClusterId}",
                        kmpConfig.getFaqSearchIndexName(),
                        questionClusterId
                )
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, res) -> {
                    /* 幂等场景不抛异常 */
                    log.info("幂等场景不抛异常: {}", questionClusterId);
                })
                .toBodilessEntity();

        int statusCode = response.getStatusCode().value();
        if (statusCode == 200 || statusCode == 204) {
            log.debug("FAQ 文档删除成功，questionClusterId={}", questionClusterId);
        } else if (statusCode == 404) {
            //文档不存在  业务正确
            log.debug("FAQ 文档不存在，questionClusterId={}", questionClusterId);
        } else {
            log.warn("FAQ 文档删除返回非预期状态码，questionClusterId={}，status={}", questionClusterId, statusCode);
            throw new ServiceException("FAQ 搜索服务暂时不可用");
        }
    }


    public List<Long> searchFaqIds(String keyword, int limit, double minScore) {

        /*
        请求体
        {
          "size": "<limit>",
          "min_score": "<minScore>",
          "_source": ["question_cluster_id"],
          "query": {
            "match": {
              "standard_question": {
                "query": "<keyword>"
              }
            }
          }
        }
         */
        //组织es请求对象
        Map<String, Object> request = new HashMap<>();

        request.put("size", limit);
        request.put("min_score", minScore);
        request.put("_source", false);

        Map<String, Object> query = new HashMap<>();
        Map<String, Object> match = new HashMap<>();

        match.put("standardQuestion", Map.of("query", keyword));
        query.put("match", match);
        request.put("query", query);

        Map response = restClient.post()
                .uri("/{indexName}/_search",
                        kmpConfig.getFaqSearchIndexName())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        /*
         响应体结构 类似
            {
              "took": 11,
              "timed_out": false,
              "_shards": {
                "total": 1,
                "successful": 1,
                "skipped": 0,
                "failed": 0
              },
              "hits": {
                "total": {
                  "value": 1,
                  "relation": "eq"
                },
                "max_score": 0.2876821,
                "hits": [
                  {
                    "_index": "goods",
                    "_id": "CFSlEaIBQydVKOFUncMg",
                    "_score": 0.2876821,
                    "_source": {
                      "title": "华为笔记本电脑"
                    }
                  }
                ]
              }
            }
         */
        Map<String,Object> hits = (Map) response.get("hits");
        List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");

        List<Long> ids = hitList.stream()
                .map(hit -> {
                    String id = (String) hit.get("_id");
                    return id == null ? null : Long.valueOf(id);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return ids;
    }
}