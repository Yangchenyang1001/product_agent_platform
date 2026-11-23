package com.ycy.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ycy.core.entity.KnowledgeGroup;

import java.util.List;

public interface KnowledgeGroupService extends IService<KnowledgeGroup> {
    IPage<KnowledgeGroup> pageKnowledgeGroups(Page<KnowledgeGroup> page, KnowledgeGroup query);

    KnowledgeGroup getKnowledgeGroupDetail(Long id);

    Long saveKnowledgeGroup(KnowledgeGroup addGroup, Long userId);

    boolean removeKnowledgeGroup(Long id, Long userId);

    List<KnowledgeGroup> listKnowledgeGroupOptions();
}
