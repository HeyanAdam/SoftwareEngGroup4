package com.group4.kaoyan.modules.chat.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.mapper.ConversationMapper;
import com.group4.kaoyan.modules.chat.mapper.MessageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聊天历史增量分页（成员 1 任务 6.2）：
 * 默认页、limit 上限、before_id 过滤、多查一条判断 has_more、返回升序。
 */
class ChatServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CONVERSATION_ID = 10L;

    static {
        // 纯单测里没有 Spring 的 Mapper 扫描，手动注册实体元数据，
        // 让 LambdaQueryWrapper 能解析 ChatMessage::getId 等列名。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChatMessage.class);
    }

    private ConversationMapper conversationMapper;
    private MessageMapper messageMapper;
    private ChatService service;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(MessageMapper.class);
        // 分页路径只用到两个 Mapper；归属校验在“本人会话”分支提前返回，
        // 不会触碰 DataScopeService / AiProperties，故无需构造它们。
        service = new ChatService(conversationMapper, messageMapper, null, null);

        ChatConversation conversation = new ChatConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setDeleted(0);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation);
    }

    /** 构造 id 从 high 递减到 low 的倒序查询结果（模拟数据库返回） */
    private static List<ChatMessage> rowsDesc(long high, long low) {
        List<ChatMessage> rows = new ArrayList<>();
        for (long id = high; id >= low; id -= 1) {
            ChatMessage message = new ChatMessage();
            message.setId(id);
            message.setConversationId(CONVERSATION_ID);
            rows.add(message);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<ChatMessage> captureQuery() {
        ArgumentCaptor<Wrapper<ChatMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper).selectList(captor.capture());
        return (LambdaQueryWrapper<ChatMessage>) captor.getValue();
    }

    @Test
    @DisplayName("不传 before_id：多查一条判断 has_more，返回最近一页且恢复升序")
    void shouldReturnLatestPageInAscendingOrder() {
        when(messageMapper.selectList(any())).thenReturn(rowsDesc(21, 1));

        ChatHistoryPage page = service.messagePage(USER_ID, CONVERSATION_ID, null, null);

        assertEquals(ChatService.DEFAULT_HISTORY_PAGE_SIZE, page.items().size());
        assertEquals(2L, page.items().get(0).getId());
        assertEquals(21L, page.items().get(19).getId());
        assertTrue(page.hasMore());
        assertEquals(2L, page.nextBeforeId());

        LambdaQueryWrapper<ChatMessage> query = captureQuery();
        assertTrue(query.getSqlSegment().contains("LIMIT 21"));
    }

    @Test
    @DisplayName("传 before_id：按 id 小于游标过滤，并用 limit + 1 探测更早消息")
    void shouldFilterByBeforeIdAndProbeOneMoreRow() {
        when(messageMapper.selectList(any())).thenReturn(rowsDesc(15, 10));

        ChatHistoryPage page = service.messagePage(USER_ID, CONVERSATION_ID, 20L, 5);

        assertEquals(5, page.items().size());
        assertEquals(11L, page.items().get(0).getId());
        assertEquals(15L, page.items().get(4).getId());
        assertTrue(page.hasMore());
        assertEquals(11L, page.nextBeforeId());

        LambdaQueryWrapper<ChatMessage> query = captureQuery();
        assertTrue(query.getSqlSegment().contains("LIMIT 6"));
        assertTrue(query.getParamNameValuePairs().containsValue(20L));
        assertTrue(query.getSqlSegment().contains("id <"));
    }

    @Test
    @DisplayName("最后一页：不足 limit 时 has_more=false，游标为该页最早一条")
    void shouldReportNoMoreWhenRowsBelowLimit() {
        when(messageMapper.selectList(any())).thenReturn(rowsDesc(3, 1));

        ChatHistoryPage page = service.messagePage(USER_ID, CONVERSATION_ID, 4L, 20);

        assertEquals(3, page.items().size());
        assertEquals(1L, page.items().get(0).getId());
        assertEquals(3L, page.items().get(2).getId());
        assertFalse(page.hasMore());
        assertEquals(1L, page.nextBeforeId());
    }

    @Test
    @DisplayName("空会话：items 为空，has_more=false，游标为空")
    void shouldReturnEmptyPageWithoutCursor() {
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());

        ChatHistoryPage page = service.messagePage(USER_ID, CONVERSATION_ID, null, 20);

        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
        assertNull(page.nextBeforeId());
    }

    @Test
    @DisplayName("limit 上限 100、非法值回退默认 20")
    void shouldClampLimit() {
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());

        service.messagePage(USER_ID, CONVERSATION_ID, null, 500);
        assertTrue(captureQuery().getSqlSegment().contains("LIMIT 101"));

        setUp();
        service.messagePage(USER_ID, CONVERSATION_ID, null, 0);
        assertTrue(captureQuery().getSqlSegment().contains("LIMIT 21"));
    }
}
