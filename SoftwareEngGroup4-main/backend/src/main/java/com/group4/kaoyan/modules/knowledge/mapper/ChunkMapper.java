package com.group4.kaoyan.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group4.kaoyan.modules.knowledge.entity.KbDocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChunkMapper extends BaseMapper<KbDocumentChunk> {

    @Delete("DELETE FROM kb_document_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM kb_document_chunk WHERE kb_id = #{kbId}")
    int deleteByKbId(@Param("kbId") Long kbId);
}
