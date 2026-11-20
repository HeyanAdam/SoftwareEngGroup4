package com.group4.kaoyan.modules.file.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.group4.kaoyan.modules.file.entity.FileUploadSession;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UploadSessionMapper {

    int insert(FileUploadSession session);

    FileUploadSession selectById(@Param("id") Long id);

    FileUploadSession selectByIdForUser(@Param("id") Long id, @Param("userId") Long userId);

    int updateProgress(@Param("id") Long id,
                       @Param("uploadedCount") Integer uploadedCount,
                       @Param("status") String status);

    /** 抢占合并：只有一个请求能把会话从 INIT/UPLOADING 推进到 MERGING。 */
    int tryStartMerging(@Param("id") Long id);

    int markCompleted(@Param("id") Long id, @Param("objectKey") String objectKey);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 过期的 INIT / UPLOADING 会话，供清理任务使用。 */
    List<FileUploadSession> selectExpired(@Param("limit") int limit);

    int cancel(@Param("id") Long id);
}
