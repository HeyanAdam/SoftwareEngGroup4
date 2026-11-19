package com.group4.kaoyan.modules.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.mapper.ConversationMapper;
import com.group4.kaoyan.modules.chat.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AiProperties props;

    public ChatConversation getOrCreate(Long userId, Long conversationId, Long kbId, String question) {
        if (conversationId != null) {
            ChatConversation c = conversationMapper.selectById(conversationId);
            if (c == null || !userId.equals(c.getUserId()) || (c.getDeleted() != null && c.getDeleted() == 1)) {
                throw new BizException(404, "会话不存在");
            }
            return c;
        }
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

    public List<ChatConversation> list(Long userId) {
        return conversationMapper.selectList(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getDeleted, 0)
                .orderByDesc(ChatConversation::getId));
    }

    public List<ChatMessage> messages(Long userId, Long conversationId) {
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null || !userId.equals(c.getUserId())) {
            throw new BizException(404, "会话不存在");
        }
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId));
    }

    public void delete(Long userId, Long conversationId) {
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null || !userId.equals(c.getUserId())) {
            throw new BizException(404, "会话不存在");
        }
        ChatConversation upd = new ChatConversation();
        upd.setId(conversationId);
        upd.setDeleted(1);
        upd.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(upd);
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
