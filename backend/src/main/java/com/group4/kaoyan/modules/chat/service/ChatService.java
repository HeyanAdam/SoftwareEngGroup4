package com.group4.kaoyan.modules.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.common.security.DataScopeService;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.mapper.ConversationMapper;
import com.group4.kaoyan.modules.chat.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话与消息（P2 任务 3：数据范围接入）。
 *
 * <p>归属校验分两层返回值，与《接口设计文档 V1.1》1.6 节对齐：</p>
 * <ul>
 *   <li>会话不存在 / 已删除 → 404 + code 404（资源不存在）；</li>
 *   <li>会话存在但不属于当前用户 → 403 + 10009（访问他人数据），
 *       由 {@link DataScopeService#requireSelf} 统一抛出，拥有 ALL 数据范围的管理员放行。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 历史分页默认页大小。 */
    public static final int DEFAULT_HISTORY_PAGE_SIZE = 20;
    /** 历史分页单次最大条数，防止客户端一次拉全量。 */
    public static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AiProperties props;
    private final DataScopeService dataScopeService;

    public ChatConversation getOrCreate(Long userId, Long conversationId, Long kbId, String question) {
        if (conversationId != null) {
            return requireOwned(userId, conversationId);
        }
        return createConversation(userId, kbId, question);
    }

    /** 新建会话（不读登录态，流式核心与后台任务可直接调用）。 */
    public ChatConversation createConversation(Long userId, Long kbId, String question) {
        ChatConversation c = new ChatConversation();
        c.setUserId(userId);
        c.setKbId(kbId);
        c.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        c.setDeleted(0);
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        conversationMapper.insert(c);
        return c;
    }

    /**
     * 严格按用户 ID 校验归属：不经过 {@link DataScopeService}（它依赖 HTTP 登录态），
     * 供流式核心 / MQ 消费者使用。管理员跨用户查看历史仍走 {@link #messages} 的宽松口径。
     */
    public ChatConversation requireOwnedByUser(Long userId, Long conversationId) {
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null || (c.getDeleted() != null && c.getDeleted() == 1)) {
            throw new BizException(404, "会话不存在");
        }
        if (userId == null || !userId.equals(c.getUserId())) {
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该数据");
        }
        return c;
    }

    /** 落库前读取会话（存在、未删除、归属正确），供幂等/重试链路复用。 */
    public ChatConversation findOwned(Long userId, Long conversationId) {
        return requireOwnedByUser(userId, conversationId);
    }

    public List<ChatConversation> list(Long userId) {
        return conversationMapper.selectList(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getDeleted, 0)
                .orderByDesc(ChatConversation::getId));
    }

    /**
     * 旧口径：一次返回全部消息（升序）。保留给未传分页参数的调用，行为不变。
     */
    public List<ChatMessage> messages(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId));
    }

    /**
     * 增量分页：返回 {@code beforeId} 之前（不含）最近一页消息。
     *
     * <p>规则与《成员 1 操作文档》6.2 一致：</p>
     * <ul>
     *   <li>{@code beforeId} 为空返回最近一页；</li>
     *   <li>{@code limit} 默认 {@value #DEFAULT_HISTORY_PAGE_SIZE}，上限 {@value #MAX_HISTORY_PAGE_SIZE}；</li>
     *   <li>按 ID 倒序多查一条判断是否还有更早消息，返回前恢复为时间升序。</li>
     * </ul>
     */
    public ChatHistoryPage messagePage(Long userId, Long conversationId, Long beforeId, Integer limit) {
        requireOwned(userId, conversationId);
        int size = normalizeHistoryLimit(limit);
        List<ChatMessage> desc = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .lt(beforeId != null, ChatMessage::getId, beforeId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = desc.size() > size;
        if (hasMore) {
            desc = new ArrayList<>(desc.subList(0, size));
        }
        Collections.reverse(desc);
        Long nextBeforeId = desc.isEmpty() ? null : desc.get(0).getId();
        return new ChatHistoryPage(desc, hasMore, nextBeforeId);
    }

    int normalizeHistoryLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_HISTORY_PAGE_SIZE;
        }
        return Math.min(limit, MAX_HISTORY_PAGE_SIZE);
    }

    public void delete(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        ChatConversation upd = new ChatConversation();
        upd.setId(conversationId);
        upd.setDeleted(1);
        upd.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(upd);
    }

    /**
     * 会话存在性与归属校验。
     *
     * <p>{@code userId} 是当前登录用户（调用方传入，便于单测）；
     * 归属判定走 {@link DataScopeService#requireSelf}，它内部取当前登录态，
     * 二者不一致说明调用方传错了 userId，这里再兜一层等值校验。</p>
     */
    private ChatConversation requireOwned(Long userId, Long conversationId) {
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null || (c.getDeleted() != null && c.getDeleted() == 1)) {
            throw new BizException(404, "会话不存在");
        }
        if (userId != null && userId.equals(c.getUserId())) {
            return c;
        }
        if (dataScopeService != null && dataScopeService.canAccess(c.getUserId())) {
            return c;
        }
        throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该数据");
    }

    public ChatMessage saveUserMessage(ChatConversation c, Long userId, String question) {
        return saveUserMessage(c, userId, question, null, ChatMessage.STATUS_COMPLETED);
    }

    /**
     * 幂等落用户消息：同一 (conversationId,userId,clientMessageId) 只建一条。
     * 异步链路首次落库状态为 PENDING，回答完成后由 {@link #markUserMessageCompleted} 收口。
     */
    public ChatMessage saveUserMessage(ChatConversation c, Long userId, String question,
                                       String clientMessageId, String status) {
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            ChatMessage existing = findByClientMessageId(c.getId(), userId, clientMessageId);
            if (existing != null) {
                return existing;
            }
            try {
                return save(c, userId, "user", question, null, ChatMessage.STATUS_PENDING,
                        clientMessageId, null, null);
            } catch (DuplicateKeyException e) {
                // 并发重复提交：唯一键兜底，回查胜出的那一条
                ChatMessage winner = findByClientMessageId(c.getId(), userId, clientMessageId);
                if (winner != null) {
                    return winner;
                }
                throw e;
            }
        }
        return save(c, userId, "user", question, null, status, null, null, null);
    }

    public ChatMessage findByClientMessageId(Long conversationId, Long userId, String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        List<ChatMessage> list = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getClientMessageId, clientMessageId)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public ChatMessage findMessage(Long messageId) {
        return messageMapper.selectById(messageId);
    }

    public ChatMessage saveAssistantMessage(ChatConversation c, Long userId,
                                            String content, String referencesJson, String modelName) {
        return save(c, userId, "assistant", content, referencesJson,
                ChatMessage.STATUS_COMPLETED, null, null, null);
    }

    /** 异步链路：回答先建 STREAMING 行，客户端/补发查询能立刻看到“生成中”。 */
    public ChatMessage createAssistantStreaming(ChatConversation c, Long userId,
                                                String streamId, Long replyToId) {
        return save(c, userId, "assistant", "", "[]", ChatMessage.STATUS_STREAMING,
                null, streamId, replyToId);
    }

    /** 消费者每 N 个事件更新一次内容与游标，崩溃后仍能恢复到最后已落库的位置。 */
    public void updateAssistantProgress(Long messageId, String content, long lastSequence) {
        ChatMessage update = new ChatMessage();
        update.setId(messageId);
        update.setContent(content);
        update.setLastSequence(lastSequence);
        messageMapper.updateById(update);
    }

    /** 进入终态：COMPLETED / FAILED，写入引用、错误与完成时间。 */
    public ChatMessage finishAssistant(Long messageId, String content, String referencesJson,
                                       String status, String errorMessage, long lastSequence) {
        messageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getContent, content == null ? "" : content)
                .set(ChatMessage::getReferencesJson, referencesJson)
                .set(ChatMessage::getStatus, status)
                .set(ChatMessage::getErrorMessage, errorMessage)
                .set(ChatMessage::getLastSequence, lastSequence)
                .set(ChatMessage::getCompletedAt, LocalDateTime.now()));
        return messageMapper.selectById(messageId);
    }

    public void markUserMessageCompleted(Long messageId) {
        ChatMessage update = new ChatMessage();
        update.setId(messageId);
        update.setStatus(ChatMessage.STATUS_COMPLETED);
        update.setCompletedAt(LocalDateTime.now());
        messageMapper.updateById(update);
    }

    /** 同一 streamId 只应有一条回答；重试时复用它，避免重复消息。 */
    public ChatMessage findAssistantByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return null;
        }
        List<ChatMessage> list = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getStreamId, streamId)
                .eq(ChatMessage::getRole, "assistant")
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** MQ 重试前把已有回答重置回 STREAMING，序号从 1 重新累计。 */
    public ChatMessage resetAssistantStreaming(Long messageId) {
        messageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getStatus, ChatMessage.STATUS_STREAMING)
                .set(ChatMessage::getContent, "")
                .set(ChatMessage::getLastSequence, 0L)
                .set(ChatMessage::getErrorMessage, null)
                .set(ChatMessage::getCompletedAt, null));
        return messageMapper.selectById(messageId);
    }

    private ChatMessage save(ChatConversation c, Long userId, String role, String content, String refs,
                             String status, String clientMessageId, String streamId, Long replyToId) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(c.getId());
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setReferencesJson(refs);
        m.setModelName(props.getLlm().getModel());
        m.setClientMessageId(clientMessageId);
        m.setStreamId(streamId);
        m.setReplyToId(replyToId);
        m.setStatus(status == null ? ChatMessage.STATUS_COMPLETED : status);
        m.setLastSequence(0L);
        m.setCreateTime(LocalDateTime.now());
        if (ChatMessage.STATUS_COMPLETED.equals(m.getStatus())) {
            m.setCompletedAt(LocalDateTime.now());
        }
        messageMapper.insert(m);
        return m;
    }

    /** 取最近 N 轮历史，格式化成提示词里的对话历史 */
    public String recentHistory(Long conversationId) {
        int rounds = Math.max(1, props.getRag().getHistoryRounds());
        List<ChatMessage> all = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + rounds * 2));
        List<ChatMessage> asc = new ArrayList<>(all);
        asc.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : asc) {
            sb.append("user".equals(m.getRole()) ? "学生：" : "助教：")
                    .append(m.getContent()).append('\n');
        }
        return sb.toString();
    }
}
