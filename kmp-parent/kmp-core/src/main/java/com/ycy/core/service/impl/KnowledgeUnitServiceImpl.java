package com.ycy.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ycy.common.core.domain.entity.SysUser;
import com.ycy.common.exception.ServiceException;
import com.ycy.core.config.KmpConfig;
import com.ycy.core.constant.KnowledgeUnitConstants;
import com.ycy.core.dto.DeleteDocumentChunksResponse;
import com.ycy.core.dto.ExternalKnowledgeTaskStatus;
import com.ycy.core.dto.KnowledgeChunk;
import com.ycy.core.dto.KnowledgeChunkPageResponse;
import com.ycy.core.entity.KnowledgeGroup;
import com.ycy.core.entity.KnowledgeImportTask;
import com.ycy.core.entity.KnowledgeUnit;
import com.ycy.core.mapper.KnowledgeUnitMapper;
import com.ycy.core.service.KnowledgeGroupService;
import com.ycy.core.service.KnowledgeUnitService;
import com.ycy.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 知识单元表 服务实现类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
@Service
public class KnowledgeUnitServiceImpl extends ServiceImpl<KnowledgeUnitMapper, KnowledgeUnit> implements KnowledgeUnitService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeUnitServiceImpl.class);


    @Autowired
    private KnowledgeGroupService knowledgeGroupService;
    @Autowired
    private KmpConfig kmpConfig; // MinIO 公网访问端点，不包对象路径。例如：http://192.168.1.100:9000/

    @Autowired
    private ISysUserService sysUserService;

    @Override
    public IPage<KnowledgeUnit> pageKnowledgeUnits(Page<KnowledgeUnit> page, KnowledgeUnit query) {
        log.info("开始分页查询知识单元，pageNum={}，pageSize={}", page.getCurrent(), page.getSize());


        LambdaQueryWrapper<KnowledgeUnit> wrapper = new LambdaQueryWrapper<KnowledgeUnit>()
                .like(query.getKnowledgeUnitName() != null, KnowledgeUnit::getKnowledgeUnitName, query.getKnowledgeUnitName())
                .eq(query.getKnowledgeGroupId() != null, KnowledgeUnit::getKnowledgeGroupId, query.getKnowledgeGroupId())
                .orderByDesc(KnowledgeUnit::getUpdateTime)
                .orderByDesc(KnowledgeUnit::getId);
        Page<KnowledgeUnit> result = this.page(page, wrapper);
        // 补知识组名称
        this.fillKnowledgeGroupNames(result.getRecords());
        // 补充文档下载地址
        this.fillOriginalFileDownloadUrls(result.getRecords());
        return result;
    }

    /**
     * 为对外返回的知识单元批量补充 MinIO 完整下载地址。
     */
    private void fillOriginalFileDownloadUrls(List<KnowledgeUnit> knowledgeUnitList) {
        for (KnowledgeUnit knowledgeUnit : knowledgeUnitList) {
            knowledgeUnit.setOriginalFileDownloadUrl(resolveDownloadUrl(knowledgeUnit.getOriginalFileDownloadUrl()));
        }
    }

    /**
     * 把库里的下载地址补成浏览器可直接访问的完整 URL。
     * <p>
     * 外部切片服务回传的可能是预签名绝对地址，也可能是不带端点的对象路径：
     * 绝对地址原样返回，只有相对路径才拼接 MinIO 端点，避免出现
     * {@code http://host:9000http://other:9000/...} 这种非法地址。
     * </p>
     *
     * @param originalFileDownloadUrl 库中存储的下载地址或对象路径
     * @return 可直接访问的完整下载地址，入参为空时返回 null
     */
    private String resolveDownloadUrl(String originalFileDownloadUrl) {
        if (originalFileDownloadUrl == null || originalFileDownloadUrl.isBlank()) {
            return null;
        }
        String downloadUrl = originalFileDownloadUrl.trim();
        // 已经带协议头，说明是完整的预签名地址，不能再拼端点。
        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            return downloadUrl;
        }
        String endpoint = kmpConfig.getMinioPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return downloadUrl;
        }
        // 端点末尾的斜杠与对象路径开头的斜杠只保留一个。
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String path = downloadUrl.startsWith("/") ? downloadUrl : "/" + downloadUrl;
        return base + path;
    }

    /**
     * 补充知识组名称
     *
     * @param records
     */
    private void fillKnowledgeGroupNames(List<KnowledgeUnit> records) {
        List<Long> groupIds = records.stream().map(KnowledgeUnit::getKnowledgeGroupId).distinct().collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return;
        }
        List<KnowledgeGroup> knowledgeGroups = knowledgeGroupService.listByIds(groupIds);
        Map<Long, String> map = knowledgeGroups.stream().collect(Collectors.toMap(KnowledgeGroup::getId, KnowledgeGroup::getKnowledgeGroupName));
        for (KnowledgeUnit record : records) {
            Long knowledgeGroupId = record.getKnowledgeGroupId();

            if (map.containsKey(knowledgeGroupId)) {
                record.setKnowledgeGroupName(map.get(knowledgeGroupId));
            }
        }
    }

    @Override
    public KnowledgeUnit getKnowledgeUnitDetail(Long id) {
        log.info("开始查询知识单元详情，knowledgeUnitId={}", id);
        KnowledgeUnit knowledgeUnit = this.getById(id);
        KnowledgeGroup knowledgeGroup = knowledgeGroupService.getById(knowledgeUnit.getKnowledgeGroupId());
        knowledgeUnit.setKnowledgeGroupName(knowledgeGroup == null ? null : knowledgeGroup.getKnowledgeGroupName());
        knowledgeUnit.setCreateUserName(this.findUserDisplayName(knowledgeUnit.getCreateUserId()));
        knowledgeUnit.setUpdateUserName(this.findUserDisplayName(knowledgeUnit.getUpdateUserId()));

        knowledgeUnit.setOriginalFileDownloadUrl(resolveDownloadUrl(knowledgeUnit.getOriginalFileDownloadUrl()));
        return knowledgeUnit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveKnowledgeUnitBaseInfo(KnowledgeUnit knowledgeUnit, Long userId) {
        Long knowledgeUnitId = knowledgeUnit.getId();
        log.info("开始修改知识单元基础信息，userId={}，knowledgeUnitId={}", userId, knowledgeUnitId);

        // 只构造可编辑字段的更新条件，防止页面篡改文档标识、文件和统计。
        this.lambdaUpdate()
                .eq(KnowledgeUnit::getId, knowledgeUnitId)
                //只修改字段白名单
                .set(KnowledgeUnit::getKnowledgeUnitName, knowledgeUnit.getKnowledgeUnitName())
                .set(KnowledgeUnit::getKnowledgeGroupId, knowledgeUnit.getKnowledgeGroupId())
                .set(KnowledgeUnit::getUpdateUserId, userId)
                .set(KnowledgeUnit::getUpdateTime, LocalDateTime.now())
                .update();

        return knowledgeUnitId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeKnowledgeUnit(Long id) {
        log.info("开始逻辑删除知识单元，knowledgeUnitId={}", id);
        // 删除知识单元下的全部切片信息
        String documentId = null;
        DeleteDocumentChunksResponse result = null;

        try {
            // 获取知识单元
            KnowledgeUnit knowledgeUnit = this.getById(id);
            // 获取文档 ID
            documentId = knowledgeUnit.getDocumentId();

            // 批量清空切片
            restClient.delete()
                    .uri("/knowledge-units/{documentId}/chunks", documentId)
                    .retrieve()
                    .body(DeleteDocumentChunksResponse.class);

        } catch (Exception e) {
            log.error("清空切片响应异常，documentId={}，result={}", documentId, result, e);
            throw new ServiceException("删除失败");
        }

        return this.removeById(id);
    }

    @Override
    public KnowledgeUnit getKnowledgeUnitByFileSha256(String fileSha256) {
        // 自动将大写/带空格的输入规范化后再校验
        String normalizedSha256 = fileSha256 == null ? null : fileSha256.trim().toLowerCase();
        if (normalizedSha256 == null || !KnowledgeUnitConstants.SHA256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new ServiceException("文件 SHA-256 格式不正确");
        }
        return this.lambdaQuery()
                .select(KnowledgeUnit::getId, KnowledgeUnit::getKnowledgeUnitName)
                .eq(KnowledgeUnit::getFileSha256, normalizedSha256)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public Long createFromCompletedImport(KnowledgeImportTask processingTask, ExternalKnowledgeTaskStatus externalStatus) {
        ExternalKnowledgeTaskStatus.Result result = externalStatus.getResult();

        KnowledgeUnit knowledgeUnit = new KnowledgeUnit();
        knowledgeUnit.setDocumentId(result.getDocumentId());
        knowledgeUnit.setKnowledgeGroupId(processingTask.getKnowledgeGroupId());
        knowledgeUnit.setKnowledgeUnitName(result.getItemName());
        knowledgeUnit.setOriginalFileName(processingTask.getFileName());
        knowledgeUnit.setFileSha256(result.getFileSha256());
        knowledgeUnit.setOriginalFileDownloadUrl(result.getOriginalFileDownloadUrl());
        knowledgeUnit.setChunkCount(result.getChunkCount());
        knowledgeUnit.setTotalTextLength(result.getTotalTextLength());
        knowledgeUnit.setCreateUserId(processingTask.getCreateUserId());
        knowledgeUnit.setCreateTime(LocalDateTime.now());
        knowledgeUnit.setIsDeleted(0);

        this.save(knowledgeUnit);
        return knowledgeUnit.getId();
    }


    private String findUserDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserService.selectUserById(userId);
        return user.getNickName();
    }


    @Autowired
    @Qualifier("knowledgeSystemRestClient")
    private RestClient restClient;


    @Override
    public KnowledgeChunk saveChunk(Long id, String chunkId, KnowledgeChunk chunk, Long userId) {
        KnowledgeUnit knowledgeUnit = this.getById(id);
        if (knowledgeUnit == null) {
            throw new ServiceException("知识单元不存在");
        }
        String documentId = knowledgeUnit.getDocumentId();
        log.info("开始保存知识单元切片，knowledgeUnitId={},documentId={}, chunkId={}", id, documentId, chunkId);

        KnowledgeChunk result;
        if (chunkId == null) {
            // 新切片
            result = restClient.post().uri("/knowledge-units/{document_id}/chunks", documentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildChunkRequest(chunk))
                    .retrieve()
                    .body(KnowledgeChunk.class);
        } else {
            // 更新切片
            result = restClient.put().uri("/knowledge-units/{document_id}/chunks/{chunkId}", documentId, chunkId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildChunkRequest(chunk))
                    .retrieve()
                    .body(KnowledgeChunk.class);
        }
        // 同步统计数据
        this.updateStatistics(id, result.getChunkCount(), result.getTotalTextLength(), userId);
        log.info("保存知识单元切片完成，knowledgeUnitId={},documentId={}, chunkId={}", id, documentId, chunkId);

        return null;
    }

    /**
     * 构造外部服务要求的JSON
     */
    private Map<String, Object> buildChunkRequest(KnowledgeChunk chunk) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chunk_order", chunk.getChunkOrder());
        request.put("title", chunk.getTitle());
        request.put("parent_title", chunk.getParentTitle());
        request.put("content", chunk.getContent());
        return request;
    }

    /**
     * 刷新本地统计。
     */
    private void updateStatistics(Long knowledgeUnitId, Long chunkCount, Long totalTextLength, Long userId) {

        LambdaUpdateWrapper<KnowledgeUnit> wrapper = new LambdaUpdateWrapper<KnowledgeUnit>()
                .eq(KnowledgeUnit::getId, knowledgeUnitId)
                .set(KnowledgeUnit::getChunkCount, chunkCount)
                .set(KnowledgeUnit::getTotalTextLength, totalTextLength)
                .set(KnowledgeUnit::getUpdateUserId, userId)
                .set(KnowledgeUnit::getUpdateTime, LocalDateTime.now());

        // 执行更新
        update(wrapper);
    }

    @Override
    public KnowledgeChunkPageResponse pageChunks(Long id, Long pageNum, Long pageSize, Long userId) {

        KnowledgeUnit knowledgeUnit = this.getById(id);
        if (knowledgeUnit == null) {
            throw new ServiceException("知识单元不存在");
        }
        String documentId = knowledgeUnit.getDocumentId();
        log.info("开始分页查询知识单元切片，knowledgeUnitId={},documentId={}, pageNum={}, pageSize={}", id, documentId, pageNum, pageSize);
        KnowledgeChunkPageResponse response = restClient.get().uri(uriBuilder -> uriBuilder
                        .path("/knowledge-units/{documentId}/chunks")
                        .queryParam("page_num", pageNum)
                        .queryParam("page_size", pageSize)
                        .build(documentId))
                .retrieve()
                .body(KnowledgeChunkPageResponse.class);
        // 同步统计数据
        this.updateStatistics(id, response.getChunkCount(), response.getTotalTextLength(), userId);
        return response;
    }

    @Override
    public KnowledgeChunk removeChunk(Long id, String chunkId, Long userId) {
        KnowledgeUnit knowledgeUnit = this.getById(id);
        String documentId = knowledgeUnit.getDocumentId();

        log.info("开始删除知识单元切片，userId={}，knowledgeUnitId={}，documentId={}，chunkId={}", userId, id, documentId, chunkId);

        KnowledgeChunk result =  restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/knowledge-units/{documentId}/chunks/{chunkId}")
                        .build(documentId, chunkId))
                .retrieve()
                .body(KnowledgeChunk.class);

        // 同步统计数据
        this.updateStatistics(id, result.getChunkCount(), result.getTotalTextLength(), userId);
        return result;
    }
}
