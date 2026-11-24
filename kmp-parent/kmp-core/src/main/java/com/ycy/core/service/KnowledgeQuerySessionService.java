package com.ycy.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycy.core.entity.KnowledgeQuerySession;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 知识查询会话索引表 服务类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
public interface KnowledgeQuerySessionService extends IService<KnowledgeQuerySession> {

    IPage<KnowledgeQuerySession> pageSessions(Page<KnowledgeQuerySession> page, String sessionTitle, Long userId);

    Long createSession(KnowledgeQuerySession session, Long userId);

    boolean removeSession(Long id, Long userId);

    boolean clearSessions(Long userId);
}
