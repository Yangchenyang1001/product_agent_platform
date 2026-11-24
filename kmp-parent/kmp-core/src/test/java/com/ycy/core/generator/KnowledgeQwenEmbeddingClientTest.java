package com.ycy.core.generator;

import com.ycy.core.client.KnowledgeQwenEmbeddingClient;
import com.ycy.core.config.KmpConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
        classes = {KmpConfig.class, KnowledgeQwenEmbeddingClient.class},
        properties = {
                "kmp.minio-public-endpoint=http://localhost:9000",          // KmpConfig:20
                "kmp.knowledge-system.base-url=http://127.0.0.1:8000",       // KmpConfig:34
                "kmp.knowledge-system.connect-timeout=3s",                   // KmpConfig:37
                "kmp.knowledge-system.read-timeout=10s",                     // KmpConfig:40
                "kmp.qwen.base-url=https://dashscope.aliyuncs.com",          // KmpConfig:62
                "kmp.qwen.embedding-path=/api/v1/services/embeddings/text-embedding/text-embedding"  // Client:30
        })
public class KnowledgeQwenEmbeddingClientTest {

    /**
     * api-key 从环境变量注入，不在代码里留明文。
     * 注解 properties 里只能写编译期常量，也无法二次解析 ${...}，所以必须走 DynamicPropertySource。
     */
    @DynamicPropertySource
    static void qwenApiKey(DynamicPropertyRegistry registry) {
        registry.add("kmp.qwen.api-key", () -> System.getenv().getOrDefault("DASHSCOPE_API_KEY", ""));
    }

    @Autowired
    private KnowledgeQwenEmbeddingClient knowledgeQwenEmbeddingClient;

    @Test
    public void embedReturnsDenseVector() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置环境变量 DASHSCOPE_API_KEY，跳过该用例");
        System.out.println("url=" + ReflectionTestUtils.getField(knowledgeQwenEmbeddingClient, "qwenBaseUrl")
                + ReflectionTestUtils.getField(knowledgeQwenEmbeddingClient, "qwenEmbeddingPath"));
        float[] vector = knowledgeQwenEmbeddingClient.embed("你好");
        System.out.println(vector.length);
    }
}
