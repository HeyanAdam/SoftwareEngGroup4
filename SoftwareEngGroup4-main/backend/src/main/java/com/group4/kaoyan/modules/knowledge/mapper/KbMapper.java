package com.group4.kaoyan.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group4.kaoyan.modules.knowledge.entity.KbKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbMapper extends BaseMapper<KbKnowledgeBase> {
}
