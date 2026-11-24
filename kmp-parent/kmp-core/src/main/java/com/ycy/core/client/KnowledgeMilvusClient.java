package com.ycy.core.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 知识库 Milvus 客户端配置
 * <p>
 * 完成 Milvus 客户端配置以及问题簇集合初始化
 */

@Slf4j
@Component
public class KnowledgeMilvusClient {
    /**
     * 向量维度，必须与百炼 embedding 返回的维度完全一致，否则写入会被 Milvus 拒绝。
     */
    @Value("${kmp.milvus.embedding-dimension:1024}")
    private int embeddingDimension;

    /**
     * Milvus 服务地址，默认本机部署。
     */
    @Value("${kmp.milvus.host:127.0.0.1}")
    private String milvusHost;

    /**
     * Milvus gRPC 端口，2.x 版本默认为 19530。
     */
    @Value("${kmp.milvus.port:19530}")
    private int milvusPort;

    /**
     * 目标数据库名；Milvus 2.x 支持多库，此处使用 default 库。
     */
    @Value("${kmp.milvus.database-name:default}")
    private String milvusDatabaseName;

    /**
     * 问题簇向量表名，同时也是建表、检索、删除时使用的唯一 Collection。
     */
    @Value("${kmp.milvus.collection-name:kmp_question_cluster}")
    private String collectionName;


    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusServiceClient() {
        //配置Milvus连接客户端
        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(milvusHost)
                        .withPort(milvusPort)
                        .withDatabaseName(milvusDatabaseName)
                        .build());

        //初始化milvusDatabaseName 集合
        initializeCollection(milvusServiceClient);



        return milvusServiceClient;
    }

    private void initializeCollection(MilvusServiceClient client) {
        // 检查集合是否存在
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collectionName).build());
        if (!exists.getData()) {
            // 集合不存在，进行初始化
            // 保证 Milvus 与 MySQL 能通过同一个 ID 对应上同一条业务数据。
            FieldType clusterId = FieldType
                    .newBuilder()
                    .withName("question_cluster_id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();
            // 字段二：知识单元 ID。作为标量过滤字段，检索时用它把召回范围限定在当前知识单元内。
            FieldType unitId = FieldType
                    .newBuilder()
                    .withName("knowledge_unit_id")
                    .withDataType(DataType.Int64)
                    .build();
            // 字段三：知识单元名称。
            FieldType itemName = FieldType
                    .newBuilder()
                    .withName("item_name")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(200)
                    .build();
            // 字段四：稠密向量。维度由配置注入。
            FieldType embedding = FieldType
                    .newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(embeddingDimension)
                    .build();
            // 建表：四个字段一次性声明。
            client.createCollection(CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .addFieldType(clusterId)
                    .addFieldType(unitId)
                    .addFieldType(itemName)
                    .addFieldType(embedding)
                    .build());
            //不需要稀疏向量
            // 建向量索引。这里建的是“稠密向量索引”。
            client.createIndex(
                    CreateIndexParam
                            .newBuilder()
                            .withCollectionName(collectionName)
                            // 给表里哪个字段建索引；只能是向量字段，这里就是存放稠密向量的 embedding 列。
                            .withFieldName("embedding")
                            // 索引名字。
                            .withIndexName("question_cluster_embedding_idx")
                            // 索引算法：IVF_FLAT = “先分桶（IVF）+ 桶内拿原始向量精确比对（FLAT）”。
                            // 建索引时把全量向量聚类切成 nlist 个桶，检索时只扫其中 nprobe 个桶，
                            // nlist默认值（126），nprobe默认值（16）。
                            // 属于近似最近邻（ANN）：数据量大时快，但可能漏掉真正最相似的那条。
                            .withIndexType(IndexType.IVF_FLAT)
                            // 相似度度量：COSINE 即余弦相似度，只看两个向量的夹角、不看长短，分数越接近 1 越相似。
                            .withMetricType(MetricType.COSINE)
                            // 同步等待索引构建完成再返回，避开刚建完表就立刻检索、索引未就绪的情境；数据量越大此处阻塞越久。
                            .withSyncMode(true)
                            // 真正发起建索引请求。
                            .build()
            );
            // 加载到内存：Milvus 只有 load 之后数据才能被检索；SyncLoad 保证本方法返回时表已可用。
            client.loadCollection(
                    LoadCollectionParam
                            .newBuilder()
                            .withCollectionName(collectionName)
                            .withSyncLoad(true)
                            .build()
            );


            // 这里可以添加集合初始化的逻辑，例如创建集合、创建索引等
            log.info("Collection {} does not exist, initializing...", collectionName);
        } else {
            log.info("Collection {} already exists", collectionName);
        }

    }

}
