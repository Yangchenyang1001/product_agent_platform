package com.ycy.core.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.common.annotation.Log;
import com.ycy.common.constant.HttpStatus;
import com.ycy.common.core.controller.BaseController;
import com.ycy.common.core.domain.AjaxResult;
import com.ycy.common.core.page.PageDomain;
import com.ycy.common.core.page.TableDataInfo;
import com.ycy.common.core.page.TableSupport;
import com.ycy.common.enums.BusinessType;
import com.ycy.core.entity.KnowledgeImportTask;
import com.ycy.core.service.KnowledgeImportTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * 知识导入任务表 前端控制器
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
@RestController
@RequestMapping("/kmp/knowledgeImportTask")
public class KnowledgeImportTaskController extends BaseController {
    @Autowired
    private KnowledgeImportTaskService knowledgeImportTaskService;

    /**
     * 分页查询平台知识导入任务。
     *
     * @return 若依分页响应
     */
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeImport:list')")
    @GetMapping("/list")
    public TableDataInfo list() {
        // 获取分页参数
        PageDomain pageDomain = TableSupport.buildPageRequest();
        // 获取当前页码
        int pageNum = pageDomain.getPageNum();
        // 获取每页大小
        int pageSize = pageDomain.getPageSize();

        // 创建分页对象
        Page<KnowledgeImportTask> page = new Page<>(pageNum, pageSize);

        // 调用服务层方法进行分页查询
        IPage<KnowledgeImportTask> result = knowledgeImportTaskService.pageImportTasks(page);

        // 创建响应对象
        TableDataInfo response = new TableDataInfo(result.getRecords(), result.getTotal());

        // 设置响应数据
        response.setCode(HttpStatus.SUCCESS);
        response.setMsg("查询成功");
        return response;
    }

    /**
     * 在初始化平台任务前检查文件摘要是否已建档。
     *
     * @param fileSha256 64位小写 SHA-256
     * @return 重复标志及已有知识单元摘要信息
     */
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeImport:import')")
    @GetMapping("/duplicate-check")
    public AjaxResult duplicateCheck(@RequestParam String fileSha256) {
        Map<String, Object> map = knowledgeImportTaskService.duplicateCheck(fileSha256);
        return AjaxResult.success(map);
    }

    /**
     * 在文件对外上传前初始化平台任务。
     *
     * @param task 文件名和知识组 ID
     * @return 平台任务 ID
     */
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeImport:import')")
    @Log(title = "知识导入", businessType = BusinessType.IMPORT)
    @PostMapping("/init")
    public AjaxResult init(@RequestBody KnowledgeImportTask task) {
        Long taskId = knowledgeImportTaskService.initImportTask(task, this.getUserId());
        return AjaxResult.success("初始化知识导入任务成功", taskId);
    }


    @PreAuthorize("@ss.hasPermi('kmp:knowledgeImport:import')")
    @Log(title = "绑定外部任务", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/external-task")
    public AjaxResult bindExternalTask(@PathVariable Long id, @RequestBody KnowledgeImportTask task) {
        KnowledgeImportTask boundTask =knowledgeImportTaskService.bindExternalTask(id, task.getExternalTaskId(), this.getUserId());
        return AjaxResult.success("绑定外部任务成功",boundTask);
    }

    /**
     * 逻辑移除未绑定待上传任务或导入失败任务。
     *
     * @param id 平台任务 ID
     * @return 移除结果
     */
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeImport:remove')")
    @Log(title = "知识导入", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        knowledgeImportTaskService.removeImportTask(id, this.getUserId());
        return AjaxResult.success("移除知识导入任务成功");
    }

}