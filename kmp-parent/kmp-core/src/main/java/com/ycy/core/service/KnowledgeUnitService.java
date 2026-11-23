package com.ycy.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.core.dto.ExternalKnowledgeTaskStatus;
import com.ycy.core.dto.KnowledgeChunk;
import com.ycy.core.dto.KnowledgeChunkPageResponse;
import com.ycy.core.entity.KnowledgeImportTask;
import com.ycy.core.entity.KnowledgeUnit;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 知识单元表 服务类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
public interface KnowledgeUnitService extends IService<KnowledgeUnit> {

    IPage<KnowledgeUnit> pageKnowledgeUnits(Page<KnowledgeUnit> page, KnowledgeUnit query);

    KnowledgeUnit getKnowledgeUnitDetail(Long id);

    Long saveKnowledgeUnitBaseInfo(KnowledgeUnit knowledgeUnit, Long userId);

    boolean removeKnowledgeUnit(Long id);

    KnowledgeUnit getKnowledgeUnitByFileSha256(String fileSha256);

    Long createFromCompletedImport(KnowledgeImportTask processingTask, ExternalKnowledgeTaskStatus externalStatus);

    KnowledgeChunk saveChunk(Long id, String chunkId, KnowledgeChunk chunk, Long userId);

    KnowledgeChunkPageResponse pageChunks(Long id, Long pageNum, Long pageSize, Long userId);

    /**
     * 删除单个外部切片。
     *
     * @param id 知识单元 ID
     * @param chunkId 切片 ID
     * @param userId 当前用户 ID
     * @return 删除后权威统计
     */
    KnowledgeChunk removeChunk(Long id, String chunkId, Long userId);
}
