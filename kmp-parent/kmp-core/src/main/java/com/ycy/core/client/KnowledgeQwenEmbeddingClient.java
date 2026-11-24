package com.ycy.core.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeQwenEmbeddingClient {

    @Value("${kmp.milvus.embedding-dimension:1024}")
    private int embeddingDimension;

    @Value("${kmp.qwen.base-url}")
    private String qwenBaseUrl;

    @Value("${kmp.qwen.api-key}")
    private String qwenApiKey;

    @Value("${kmp.qwen.embedding-path}")
    private String qwenEmbeddingPath;

    @Value("${kmp.qwen.embedding-model:text-embedding-v4}")
    private String qwenEmbeddingModel;

    @Autowired
    @Qualifier("qwenRestClient")
    private RestClient restClient;

    /**
     * 为单段文本生成固定维度稠密向量。
     *
     * @param text 待向量化文本
     * @return 浮点向量
     */
    public float[] embed(String text) {
        JSONObject response = restClient
                .post()
                .uri(qwenBaseUrl + qwenEmbeddingPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + qwenApiKey)
                .body(buildEmbeddingRequest(text))
                .retrieve()
                .body(JSONObject.class);

        JSONArray embeddings = response.getJSONObject("output").getJSONArray("embeddings");
        JSONArray values = embeddings.getJSONObject(0).getJSONArray("embedding");

        float[] vector = new float[embeddingDimension];
        for (int i = 0; i < embeddingDimension; i++) {
            vector[i] = values.getFloatValue(i);
        }
        return vector;
    }

    /**
     * 组装百炼 embedding 请求体
     *
     * @param text 待向量化文本
     * @return 待序列化的请求参数
     */
    private Map<String, Object> buildEmbeddingRequest(String text) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("dimension", embeddingDimension);
        parameters.put("output_type", "dense");

        Map<String, Object> input = new HashMap<>();
        input.put("texts", List.of(text));

        Map<String, Object> request = new HashMap<>();
        request.put("model", qwenEmbeddingModel);
        request.put("input", input);
        request.put("parameters", parameters);
        return request;
    }
}
