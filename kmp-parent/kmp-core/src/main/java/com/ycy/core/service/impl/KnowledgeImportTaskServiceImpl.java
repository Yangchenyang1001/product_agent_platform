package com.ycy.core.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ycy.common.core.domain.entity.SysUser;
import com.ycy.common.exception.ServiceException;
import com.ycy.core.config.KmpConfig;
import com.ycy.core.constant.KnowledgeImportConstants;
import com.ycy.core.dto.ExternalKnowledgeTaskStatus;
import com.ycy.core.entity.KnowledgeImportTask;
import com.ycy.core.entity.KnowledgeUnit;
import com.ycy.core.mapper.KnowledgeImportTaskMapper;
import com.ycy.core.service.KnowledgeImportTaskService;
import com.ycy.core.service.KnowledgeUnitService;
import com.ycy.system.service.ISysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 知识导入任务表 服务实现类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
@Slf4j // 日志
@Service
public class KnowledgeImportTaskServiceImpl extends ServiceImpl<KnowledgeImportTaskMapper, KnowledgeImportTask> implements KnowledgeImportTaskService {


    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private KnowledgeUnitService knowledgeUnitService;


    @Override
    public IPage<KnowledgeImportTask> pageImportTasks(Page<KnowledgeImportTask> page) {
        log.info("开始查询知识导入任务");
        LambdaQueryWrapper<KnowledgeImportTask> wrapper = new LambdaQueryWrapper<KnowledgeImportTask>()
                .orderByDesc(KnowledgeImportTask::getCreateTime)
                .orderByDesc(KnowledgeImportTask::getId);

        //执行查询
        IPage<KnowledgeImportTask> result = this.page(page, wrapper);

        //补充用户昵称
        this.fillCreateUserNames(result.getRecords());
        return result;
    }

    @Override
    public Map<String, Object> duplicateCheck(String fileSha256) {

        // sha256相当于文件的指纹，比较指纹相同则说明 知识单元 重复
        KnowledgeUnit duplicateUnit = knowledgeUnitService.getKnowledgeUnitByFileSha256(fileSha256);
        Map<String, Object> result = new HashMap<>();

        // != null 说明 知识单元 重复
        result.put("duplicate", duplicateUnit != null);
        if (duplicateUnit != null) {
            // 知识单元 重复，则返回 知识单元 ID 和 知识单元名称
            result.put("knowledgeUnitId", duplicateUnit.getId());
            result.put("knowledgeUnitName", duplicateUnit.getKnowledgeUnitName());
        }
        return result;
    }


    /**
     * 批量补充当前页创建人名称，避免同一用户被重复查询。
     */
    private void fillCreateUserNames(List<KnowledgeImportTask> taskList) {
        Map<Long, String> userNameMap = new HashMap<>();
        for (KnowledgeImportTask task : taskList) {
            Long createUserId = task.getCreateUserId();
            if (!userNameMap.containsKey(createUserId)) {
                SysUser user = sysUserService.selectUserById(createUserId);
                userNameMap.put(createUserId, user.getNickName());
            }
            task.setCreateUserName(userNameMap.get(createUserId));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long initImportTask(KnowledgeImportTask task, Long userId) {
        log.info("开始初始化知识导入任务");

        // 初始化接口只接受文件名和知识组，其余字段全部由平台重置，防止客户端越权赋值。
        // 这样做是在划安全边界：调用方可以说明“文件叫什么、放进哪个组”，但不能伪造任务 ID、处理状态、创建人和完成时间。
        LocalDateTime now = LocalDateTime.now();
        task.setId(null);
        task.setKnowledgeUnitId(null);
        task.setExternalTaskId(null);
        task.setTaskStatus(KnowledgeImportConstants.TASK_STATUS_PENDING);
        task.setTaskInfo(null);
        task.setFailureReason(null);
        task.setFinishTime(null);
        task.setDurationMs(null);
        task.setCreateUserId(userId);
        task.setCreateTime(now);
        task.setUpdateUserId(null);
        task.setUpdateTime(null);
        task.setIsDeleted(0);
        task.setCreateUserName(null);
        save(task);
        return task.getId();
    }

    @Override
    @Transactional
    public KnowledgeImportTask bindExternalTask(Long id, String externalTaskId, Long userId) {
        log.info("开始绑定外部知识导入任务，taskId={}，externalTaskId={}", id, externalTaskId);

        LocalDateTime now = LocalDateTime.now();
        boolean updated = lambdaUpdate()
                .eq(KnowledgeImportTask::getId, id)
                .eq(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_PENDING)
                .isNull(KnowledgeImportTask::getExternalTaskId)
                .set(KnowledgeImportTask::getExternalTaskId, externalTaskId)
                .set(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_PROCESSING)
                .set(KnowledgeImportTask::getUpdateUserId, userId)
                .set(KnowledgeImportTask::getUpdateTime, now)
                .update();
        if (!updated) {
            throw new ServiceException("当前状态不允许绑定外部任务");
        }
        return this.getById(id);
    }

    @Autowired
    private KmpConfig kmpConfig;

    @Autowired
    @Qualifier("knowledgeSystemRestClient")
    private RestClient restClient;

    @Override
    public void pollProcessingTasks() {
        List<KnowledgeImportTask> processingTaskList = this.lambdaQuery()
                // 查询 处理中 状态的任务
                .eq(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_PROCESSING)
                .isNotNull(KnowledgeImportTask::getExternalTaskId)
                .orderByAsc(KnowledgeImportTask::getUpdateTime)
                .orderByAsc(KnowledgeImportTask::getId)
                .last("LIMIT " + kmpConfig.getKnowledgeImportBatchSize())
                .list();
        log.info("开始轮询知识导入任务");
        for (KnowledgeImportTask processingTask : processingTaskList) {
            this.processingTask(processingTask);
        }
        log.info("完成轮询知识导入任务");
    }


    @Transactional(rollbackFor = Exception.class) // @Transactional 不能用于private方法上，因此设置为protected
    protected void processingTask(KnowledgeImportTask processingTask) {
        String externalTaskId = processingTask.getExternalTaskId();
        ExternalKnowledgeTaskStatus externalStatus = null;
        try {
            externalStatus = restClient.get()
                    .uri("/status/{taskId}", externalTaskId)
                    .retrieve()
                    .body(ExternalKnowledgeTaskStatus.class);

            String status = externalStatus.getStatus();
            Integer targetStatus = switch (status) {
                case "processing" -> KnowledgeImportConstants.TASK_STATUS_PROCESSING;
                case "completed" -> KnowledgeImportConstants.TASK_STATUS_SUCCESS;
                case "failed" -> KnowledgeImportConstants.TASK_STATUS_FAILED;
                // 未知状态按处理中兜底。
                default -> KnowledgeImportConstants.TASK_STATUS_PROCESSING;
            };
            LocalDateTime now = LocalDateTime.now();

            if (KnowledgeImportConstants.TASK_STATUS_SUCCESS.equals(targetStatus)) {
                // 创建知识单元
                Long knowledgeUnitId = knowledgeUnitService.createFromCompletedImport(processingTask, externalStatus);

                // 修改任务状态为成功。
                LambdaUpdateWrapper<KnowledgeImportTask> taskUpdate = new LambdaUpdateWrapper<KnowledgeImportTask>()
                        .eq(KnowledgeImportTask::getId, processingTask.getId())
                        .eq(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_PROCESSING)
                        .isNull(KnowledgeImportTask::getKnowledgeUnitId)
                        .set(KnowledgeImportTask::getKnowledgeUnitId, knowledgeUnitId)
                        .set(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_SUCCESS)
                        .set(KnowledgeImportTask::getTaskInfo, JSON.toJSONString(externalStatus))
                        .set(KnowledgeImportTask::getFailureReason, null)
                        .set(KnowledgeImportTask::getFinishTime, now)
                        .set(KnowledgeImportTask::getUpdateTime, now)
                        .set(KnowledgeImportTask::getDurationMs, Duration.between(processingTask.getCreateTime(), now).toMillis());

                this.update(taskUpdate);
            } else {
                // 修改任务状态
                LambdaUpdateWrapper<KnowledgeImportTask> updateWrapper = new LambdaUpdateWrapper<KnowledgeImportTask>()
                        .eq(KnowledgeImportTask::getId, processingTask.getId())
                        .eq(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_PROCESSING)
                        .set(KnowledgeImportTask::getTaskStatus, targetStatus)
                        .set(KnowledgeImportTask::getTaskInfo, JSON.toJSONString(externalStatus))
                        .set(KnowledgeImportTask::getFailureReason, null)
                        .set(KnowledgeImportTask::getUpdateTime, now);

                // 判断是否是失败状态（最终状态）
                if (KnowledgeImportConstants.TASK_STATUS_FAILED.equals(targetStatus)) {
                    updateWrapper
                            .set(KnowledgeImportTask::getFinishTime, now) //任务结束时间
                            .set(KnowledgeImportTask::getDurationMs, Duration.between(processingTask.getCreateTime(), now).toMillis()); //耗时
                }

                // 执行更新
                this.update(updateWrapper);
            }
        } catch (Exception e) {
            log.error("处理知识导入任务异常，externalTaskId={}", externalTaskId, e);
            this.markImportTaskFailed(processingTask, externalStatus, "处理知识导入任务异常");
        }


    }

    /**
     * 出现异常时修改任务状态为失败
     * @param task
     * @param externalStatus
     * @param failureReason
     */
    private void markImportTaskFailed(KnowledgeImportTask task, ExternalKnowledgeTaskStatus externalStatus, String failureReason)
    {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<KnowledgeImportTask> updateWrapper =
                new LambdaUpdateWrapper<KnowledgeImportTask>()
                        .eq(KnowledgeImportTask::getId, task.getId())
                        .eq(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_PROCESSING)
                        .isNull(KnowledgeImportTask::getKnowledgeUnitId)
                        .set(KnowledgeImportTask::getTaskStatus, KnowledgeImportConstants.TASK_STATUS_FAILED)
                        .set(KnowledgeImportTask::getTaskInfo, JSON.toJSONString(externalStatus))
                        .set(KnowledgeImportTask::getFailureReason, failureReason)
                        .set(KnowledgeImportTask::getFinishTime, now)
                        .set(KnowledgeImportTask::getDurationMs, Duration.between(task.getCreateTime(), now).toMillis())
                        .set(KnowledgeImportTask::getUpdateTime, now);
        this.update(updateWrapper);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeImportTask(Long id, Long userId) {
        log.info("开始移除知识导入任务，userId={}，taskId={}", userId, id);
        KnowledgeImportTask task = getById(id);

        boolean pendingAndUnbound = KnowledgeImportConstants.TASK_STATUS_PENDING.equals(task.getTaskStatus())
                && task.getExternalTaskId() == null;
        boolean failed = KnowledgeImportConstants.TASK_STATUS_FAILED.equals(task.getTaskStatus());
        if (!pendingAndUnbound && !failed)
        {
            throw new ServiceException("当前状态不允许移除");
        }

        LambdaQueryWrapper<KnowledgeImportTask> updateWrapper = new LambdaQueryWrapper<KnowledgeImportTask>()
                .eq(KnowledgeImportTask::getId, id)
                .eq(KnowledgeImportTask::getTaskStatus, task.getTaskStatus());

        return this.remove(updateWrapper);
    }


}
