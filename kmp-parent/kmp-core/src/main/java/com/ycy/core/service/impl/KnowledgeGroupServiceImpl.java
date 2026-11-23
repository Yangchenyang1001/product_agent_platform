package com.ycy.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ycy.common.core.domain.entity.SysUser;
import com.ycy.common.exception.ServiceException;
import com.ycy.common.utils.StringUtils;
import com.ycy.core.entity.KnowledgeGroup;
import com.ycy.core.mapper.KnowledgeGroupMapper;
import com.ycy.core.service.KnowledgeGroupService;
import com.ycy.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KnowledgeGroupServiceImpl extends ServiceImpl<KnowledgeGroupMapper, KnowledgeGroup>
        implements KnowledgeGroupService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGroupServiceImpl.class);

    @Autowired
    private ISysUserService sysUserService;


    @Override
    public IPage<KnowledgeGroup> pageKnowledgeGroups(Page<KnowledgeGroup> page, KnowledgeGroup query) {
        log.info("分页查询知识组列表，参数：{}", query);

        String normalizedName = StringUtils.trimToNull(query.getKnowledgeGroupName());
        LambdaQueryWrapper<KnowledgeGroup> queryWrapper = new LambdaQueryWrapper<KnowledgeGroup>();
        queryWrapper.like(StringUtils.isNotBlank(normalizedName), KnowledgeGroup::getKnowledgeGroupName, normalizedName);
        queryWrapper.orderByDesc(KnowledgeGroup::getUpdateTime).orderByDesc(KnowledgeGroup::getId);
        return baseMapper.selectPage(page, queryWrapper);
    }

    @Override
    public KnowledgeGroup getKnowledgeGroupDetail(Long id) {
        log.info("获取知识组详情，id={}", id);
        KnowledgeGroup knowledgeGroup = baseMapper.selectById(id);
        if (knowledgeGroup == null) {
            throw new ServiceException("知识组不存在或已删除");
        }
        knowledgeGroup.setCreateUserName(this.findUserDisplayName(knowledgeGroup.getCreateUserId()));
        knowledgeGroup.setUpdateUserName(this.findUserDisplayName(knowledgeGroup.getUpdateUserId()));
        return knowledgeGroup;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveKnowledgeGroup(KnowledgeGroup group, Long userId) {
        boolean editing = group.getId() != null;
        log.info("开始{}知识组", editing ? "编辑" : "新增");
        LocalDateTime now = LocalDateTime.now();
        if (editing) {
            log.info("编辑知识组，id={}", group.getId());
            if (this.getById(group.getId()) == null)
            {
                throw new ServiceException("知识组不存在或已删除");
            }
            group.setCreateUserId(null);
            group.setCreateTime(null);
            group.setUpdateUserId(userId);
            group.setUpdateTime(now);
            group.setIsDeleted(null);

        } else {
            log.info("新增知识组，名称={}", group.getKnowledgeGroupName());
            group.setId(null);
            group.setCreateUserId(userId);
            group.setCreateTime(now);
            group.setUpdateUserId(null);
            group.setUpdateTime(null);
            group.setIsDeleted(0);
        }

        // 如果当前知识组被设置为默认知识组，则将其他知识组的默认设置取消
        if (Integer.valueOf(1).equals(group.getIsDefault()))
        {
            Long currentId = group.getId();
            this.lambdaUpdate()
                    .eq(KnowledgeGroup::getIsDefault, 1)
                    .ne(currentId != null, KnowledgeGroup::getId, currentId)
                    .set(KnowledgeGroup::getIsDefault, 0)
                    .set(KnowledgeGroup::getUpdateUserId, userId)
                    .set(KnowledgeGroup::getUpdateTime, now)
                    .update();
        }

        this.saveOrUpdate(group);
        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeKnowledgeGroup(Long id, Long userId)
    {
        log.info("删除知识组，userId={}，knowledgeGroupId={}", userId, id);

        KnowledgeGroup knowledgeGroup = this.getById(id);
        if (knowledgeGroup == null)
        {
            throw new ServiceException("知识组不存在或已删除");
        }

        if (Integer.valueOf(1).equals(knowledgeGroup.getIsDefault()))
        {
            log.warn("知识组删除被阻止，userId={}，knowledgeGroupId={}，reason=default", userId, id);
            throw new ServiceException("默认知识组不能删除，请先取消默认设置");
        }

        return this.removeById(id);
    }

    @Override
    public List<KnowledgeGroup> listKnowledgeGroupOptions()
    {
        List<KnowledgeGroup> groupOptions = this.lambdaQuery()
                // 这里只查三列，够选择知识组即可。
                .select(
                        KnowledgeGroup::getId,
                        KnowledgeGroup::getKnowledgeGroupName,
                        KnowledgeGroup::getIsDefault
                )
                // 先按 `isDefault` 倒序，默认组自然排到最前面
                .orderByDesc(KnowledgeGroup::getIsDefault)
                // 名称相同时再用 ID 保证顺序稳定。
                .orderByAsc(KnowledgeGroup::getKnowledgeGroupName)
                .orderByAsc(KnowledgeGroup::getId)
                .list();
        return groupOptions;
    }

    /**
     * 根据用户 ID 获取用于展示的昵称。
     */
    private String findUserDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserService.selectUserById(userId);
        return user.getNickName();
    }
}
