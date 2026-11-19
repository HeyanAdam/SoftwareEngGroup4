package com.group4.kaoyan.platform.messaging.mapper;

import com.group4.kaoyan.platform.messaging.entity.AsyncTask;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AsyncTaskMapper {

    int insert(AsyncTask task);

    AsyncTask selectById(@Param("id") Long id);

    /** 业务对象最近一条任务：文档上传后按 biz 找回任务，避免重复创建。 */
    AsyncTask selectLatestByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    List<AsyncTask> selectPage(@Param("taskType") String taskType,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long countPage(@Param("taskType") String taskType, @Param("status") String status);

    int markRunning(@Param("id") Long id, @Param("progress") Integer progress);

    int finish(@Param("id") Long id,
               @Param("status") String status,
               @Param("resultJson") Object resultJson,
               @Param("errorMessage") String errorMessage);

    int scheduleRetry(@Param("id") Long id,
                      @Param("errorMessage") String errorMessage,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt);

    int cancel(@Param("id") Long id);

    /** RUNNING 且 update_time 早于指定时间的任务（卡死回收）。 */
    List<AsyncTask> selectStaleRunning(@Param("staleBefore") LocalDateTime staleBefore,
                                       @Param("limit") int limit);

    int recover(@Param("id") Long id);
}
