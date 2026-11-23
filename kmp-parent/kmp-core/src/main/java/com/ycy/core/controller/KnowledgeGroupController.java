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
import com.ycy.common.exception.ServiceException;
import com.ycy.core.entity.KnowledgeGroup;
import com.ycy.core.service.KnowledgeGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/kmp/knowledgeGroup")
public class KnowledgeGroupController extends BaseController {

    @Autowired
    private KnowledgeGroupService knowledgeGroupService;


    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeGroup:list')")
    public TableDataInfo list(KnowledgeGroup query) {
        // 获取分页参数
        PageDomain pageDomain = TableSupport.buildPageRequest();
        // 获取当前页码
        int pageNum = pageDomain.getPageNum();
        // 获取每页大小
        int pageSize = pageDomain.getPageSize();

        // 创建分页对象
        Page<KnowledgeGroup> page = new Page<>(pageNum, pageSize);

        // 调用服务层方法进行分页查询
        IPage<KnowledgeGroup> result = knowledgeGroupService.pageKnowledgeGroups(page, query);
        // 创建响应对象
        TableDataInfo response = new TableDataInfo(result.getRecords(), result.getTotal());

        // 设置响应数据
        response.setCode(HttpStatus.SUCCESS);
        response.setMsg("查询成功");
        return response;
    }


    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeGroup:query')")
    public AjaxResult queryById(@PathVariable Long id) {
        KnowledgeGroup knowledgeGroupDetail = knowledgeGroupService.getKnowledgeGroupDetail(id);
        return AjaxResult.success(knowledgeGroupDetail);
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeGroup:add')")
    @Log(title = "知识组管理", businessType = BusinessType.INSERT)
    public AjaxResult save(@Validated @RequestBody KnowledgeGroup addGroup) {
        addGroup.setId(null);
        Long id = knowledgeGroupService.saveKnowledgeGroup(addGroup, this.getUserId());
        return AjaxResult.success("新增知识组成功", id);
    }


    @PreAuthorize("@ss.hasPermi('kmp:knowledgeGroup:edit')")
    @Log(title = "知识组管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody KnowledgeGroup knowledgeGroup)
    {
        if (knowledgeGroup.getId() == null || knowledgeGroup.getId() <= 0)
        {
            throw new ServiceException("知识组 ID 不正确");
        }
        knowledgeGroupService.saveKnowledgeGroup(knowledgeGroup, this.getUserId());
        return AjaxResult.success("修改知识组成功");
    }


    @PreAuthorize("@ss.hasPermi('kmp:knowledgeGroup:remove')")
    @Log(title = "知识组管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        knowledgeGroupService.removeKnowledgeGroup(id, this.getUserId());
        return AjaxResult.success("删除知识组成功");
    }


    /**
     * 查询未删除知识组，供其他位置的下拉列表选择。
     *
     * @return 知识组选项
     */
    @PreAuthorize("@ss.hasPermi('kmp:knowledgeGroup:list')") // 权限校验
    @GetMapping("/groupOptions")
    public AjaxResult groupOptions()
    {
        return AjaxResult.success(knowledgeGroupService.listKnowledgeGroupOptions());
    }
}
