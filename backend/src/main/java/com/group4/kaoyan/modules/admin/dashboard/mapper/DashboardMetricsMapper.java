package com.group4.kaoyan.modules.admin.dashboard.mapper;

import com.group4.kaoyan.modules.admin.dashboard.dto.DailyCountDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营大屏统计聚合查询。
 *
 * <p>只做 {@code COUNT} / {@code SUM} / 日期分组，不读取聊天正文、邮箱、对象存储 key。
 * 时间范围一律使用左闭右开区间 {@code [start, end)}，由 SQL 走索引，
 * 禁止写成 {@code DATE(create_time) = CURRENT_DATE()}。</p>
 */
@Mapper
public interface DashboardMetricsMapper {

    /** 用户总数。sys_user 没有逻辑删除字段，不加 deleted 条件。 */
    long countUsers();

    /** 启用用户数（status = 1）。 */
    long countEnabledUsers();

    /**
     * 指定时间范围内、指定结果的登录次数。
     *
     * @param success 1 = 成功，0 = 失败
     */
    long countLogins(@Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end,
                     @Param("success") int success);

    /** 未删除文件数。 */
    long countFiles();

    /** 未删除文件占用字节总数；无数据时由 SQL 的 COALESCE 返回 0。 */
    long sumFileSize();

    /** 文档总数（kb_document 没有逻辑删除字段）。 */
    long countDocuments();

    /** 解析失败文档数（status = 'FAILED'）。 */
    long countFailedDocuments();

    /** 未删除会话数。 */
    long countConversations();

    /** 聊天消息总数。 */
    long countMessages();

    /** 指定时间范围内的聊天消息数。 */
    long countMessagesBetween(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /** 每日成功登录数，只返回有数据的日期。 */
    List<DailyCountDTO> dailyLogins(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** 每日文件上传数，只返回有数据的日期。 */
    List<DailyCountDTO> dailyUploads(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /** 每日聊天消息数，只返回有数据的日期。 */
    List<DailyCountDTO> dailyChatMessages(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}
