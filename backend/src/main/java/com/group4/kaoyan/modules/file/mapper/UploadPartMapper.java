package com.group4.kaoyan.modules.file.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.group4.kaoyan.modules.file.entity.FileUploadPart;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UploadPartMapper {

    /** MySQL UPSERT：唯一键 (session_id, part_number) 冲突时更新分片信息。 */
    int upsertPart(FileUploadPart part);

    FileUploadPart selectBySessionAndNumber(@Param("sessionId") Long sessionId,
                                           @Param("partNumber") Integer partNumber);

    List<FileUploadPart> selectBySession(@Param("sessionId") Long sessionId);

    int countUploaded(@Param("sessionId") Long sessionId);

    int deleteBySession(@Param("sessionId") Long sessionId);
}
