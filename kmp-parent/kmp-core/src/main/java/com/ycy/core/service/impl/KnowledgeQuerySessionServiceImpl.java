package com.ycy.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ycy.core.entity.KnowledgeQuerySession;
import com.ycy.core.mapper.KnowledgeQuerySessionMapper;
import com.ycy.core.service.KnowledgeQuerySessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 知识查询会话索引表 服务实现类
 * </p>
 *
 * @author ycy
 * @since 2026-11-19
 */
@Service
public class KnowledgeQuerySessionServiceImpl extends ServiceImpl<KnowledgeQuerySessionMapper, KnowledgeQuerySession> implements KnowledgeQuerySessionService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeQuerySessionServiceImpl.class);





    @Override
    public IPage<KnowledgeQuerySession> pageSessions(Page<KnowledgeQuerySession> page, String sessionTitle, Long userId) {
        log.info("开始分页查询知识会话，userId={}，sessionTitle={}", userId, sessionTitle == null);

        LambdaQueryWrapper<KnowledgeQuerySession> wrapper =
                new LambdaQueryWrapper<KnowledgeQuerySession>()
                        .eq(KnowledgeQuerySession::getCreateUserId, userId)
                        .like(sessionTitle != null, KnowledgeQuerySession::getSessionTitle, sessionTitle)
                        .orderByDesc(KnowledgeQuerySession::getCreateTime)
                        .orderByDesc(KnowledgeQuerySession::getId);
        IPage<KnowledgeQuerySession> result = page(page, wrapper);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(KnowledgeQuerySession session, Long userId) {
        String sessionTitle = session.getSessionTitle();
        log.info("开始创建知识查询会话", userId, sessionTitle);

        // 所有主键和审计字段均以登录上下文及服务端时间为准。
        session.setId(null);
        session.setSessionTitle(sessionTitle);
        session.setCreateUserId(userId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateUserId(null);
        session.setUpdateTime(null);
        session.setIsDeleted(0);
        save(session);

        return session.getId();
    }

    @Autowired
    @Qualifier("knowledgeSystemRestClient")
    RestClient restClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeSession(Long id, Long userId) {
        log.info("开始删除知识查询会话，userId={}，sessionId={}", userId, id);

        LocalDateTime now = LocalDateTime.now();
        log.info("调用外部知识库清除聊天记录，sessionId={}", id);

        //远程调用删除外部知识库会话  聊天记录
        restClient.delete().uri("/history/{session_id}", id)
                .retrieve().toBodilessEntity();

        // 如果想在逻辑删除时同时更新审计字段（更新用户、更新时间），可以使用以下代码显示做update操作
        LambdaUpdateWrapper<KnowledgeQuerySession> wrapper =
                new LambdaUpdateWrapper<KnowledgeQuerySession>()
                        .eq(KnowledgeQuerySession::getId, id)
                        .eq(KnowledgeQuerySession::getCreateUserId, userId)
                        .eq(KnowledgeQuerySession::getIsDeleted, 0)
                        .set(KnowledgeQuerySession::getIsDeleted, 1)
                        .set(KnowledgeQuerySession::getUpdateUserId, userId)
                        .set(KnowledgeQuerySession::getUpdateTime, now);
        return update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearSessions(Long userId) {
        log.info("开始清理知识查询会话，userId={}", userId);
        //获取所有sessionid
        LambdaQueryWrapper<KnowledgeQuerySession> queryWrapper =
                new LambdaQueryWrapper<KnowledgeQuerySession>()
                        .select(KnowledgeQuerySession::getId)
                        .eq(KnowledgeQuerySession::getCreateUserId, userId)
                        .eq(KnowledgeQuerySession::getIsDeleted, 0);

        List<KnowledgeQuerySession> sessionList = this.list(queryWrapper);
        if (sessionList == null || sessionList.size() == 0) {
            return false;
        }
        List<Long> sessionIds = sessionList.stream().map(KnowledgeQuerySession::getId).collect(Collectors.toList());
        log.info("调用外部知识库清除聊天记录，sessionIds={}", sessionIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        restClient.post().uri("/history/clear_all")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("session_ids", sessionIds))
                .retrieve()
                .toBodilessEntity();

        LocalDateTime now = LocalDateTime.now();

        // 只清理当前用户的有效索引，并同步维护审计字段。
        LambdaUpdateWrapper<KnowledgeQuerySession> wrapper =
                new LambdaUpdateWrapper<KnowledgeQuerySession>()
                        .eq(KnowledgeQuerySession::getCreateUserId, userId)
                        .eq(KnowledgeQuerySession::getIsDeleted, 0)
                        .set(KnowledgeQuerySession::getIsDeleted, 1)
                        .set(KnowledgeQuerySession::getUpdateUserId, userId)
                        .set(KnowledgeQuerySession::getUpdateTime, now);
        return update(wrapper);
    }
}
