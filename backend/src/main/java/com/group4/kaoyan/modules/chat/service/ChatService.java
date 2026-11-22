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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public List<ChatMessage> messages(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId));
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
        return save(c, userId, "user", question, null);
    }

    public ChatMessage saveAssistantMessage(ChatConversation c, Long userId,
                                            String content, String referencesJson, String modelName) {
        return save(c, userId, "assistant", content, referencesJson);
    }

    private ChatMessage save(ChatConversation c, Long userId, String role, String content, String refs) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(c.getId());
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setReferencesJson(refs);
        m.setModelName(props.getLlm().getModel());
        m.setCreateTime(LocalDateTime.now());
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
