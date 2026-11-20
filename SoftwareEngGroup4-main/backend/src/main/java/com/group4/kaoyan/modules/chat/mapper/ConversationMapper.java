package com.group4.kaoyan.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<ChatConversation> {
}
