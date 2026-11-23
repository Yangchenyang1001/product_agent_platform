package com.ycy.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ycy.core.entity.KnowledgeImportTask;

import java.util.Map;

/**
 * <p>
 * 知识导入任务表 服务类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
public interface KnowledgeImportTaskService extends IService<KnowledgeImportTask> {

    IPage<KnowledgeImportTask> pageImportTasks(Page<KnowledgeImportTask> page);

     Map<String, Object> duplicateCheck(String fileSha256);

    Long initImportTask(KnowledgeImportTask task, Long userId);

    KnowledgeImportTask bindExternalTask(Long id, String externalTaskId, Long userId);

    void pollProcessingTasks();

    boolean removeImportTask(Long id, Long userId);
}
