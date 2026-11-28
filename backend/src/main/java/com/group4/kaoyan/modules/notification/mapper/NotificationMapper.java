package com.group4.kaoyan.modules.notification.mapper;

import com.group4.kaoyan.modules.notification.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper {

    UserNotification selectById(@Param("id") Long id);

    UserNotification selectByDedupKey(@Param("dedupKey") String dedupKey);

    long countUnread(@Param("userId") Long userId);

    long countByStatus(@Param("userId") Long userId,
                       @Param("unreadOnly") boolean unreadOnly);

    List<UserNotification> selectPage(@Param("userId") Long userId,
                                      @Param("unreadOnly") boolean unreadOnly,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int insert(UserNotification notification);

    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);

    int markAllRead(@Param("userId") Long userId,
                    @Param("readAt") LocalDateTime readAt);
}
