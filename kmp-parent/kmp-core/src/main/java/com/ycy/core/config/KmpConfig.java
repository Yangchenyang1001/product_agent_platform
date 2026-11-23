package com.ycy.core.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Data
@Component
@EnableScheduling
public class KmpConfig {
    /** MinIO 公网访问端点，不包对象路径。 */
    @Value("${kmp.minio-public-endpoint}")
    private String minioPublicEndpoint;


    @Value("${kmp.knowledge-import.poll-interval-ms:5000}")
    private long knowledgeImportPollIntervalMs;

    @Value("${kmp.knowledge-import.batch-size:100}")
    private int knowledgeImportBatchSize;

    @Value("${kmp.knowledge-import.pending-expire-minutes:30}")
    private long knowledgeImportPendingExpireMinutes;


    @Value("${kmp.knowledge-system.base-url}")
    private String knowledgeSystemBaseUrl;

    @Value("${kmp.knowledge-system.connect-timeout}")
    private Duration knowledgeSystemConnectTimeout;

    @Value("${kmp.knowledge-system.read-timeout}")
    private Duration knowledgeSystemReadTimeout;

    /**
     * RestClient客户端：调用知识库接口
     *
     * @return RestClient
     */
    @Bean("knowledgeSystemRestClient")
    public RestClient knowledgeSystemRestClient()
    {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(knowledgeSystemConnectTimeout);
        requestFactory.setReadTimeout(knowledgeSystemReadTimeout);

        return RestClient
                .builder()
                .requestFactory(requestFactory)
                .baseUrl(knowledgeSystemBaseUrl)
                .build();
    }
}
