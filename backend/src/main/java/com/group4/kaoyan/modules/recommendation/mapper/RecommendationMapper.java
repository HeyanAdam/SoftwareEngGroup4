package com.group4.kaoyan.modules.recommendation.mapper;

import com.group4.kaoyan.modules.recommendation.entity.UserRecommendation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户推荐 Mapper。
 */
@Mapper
public interface RecommendationMapper {

    UserRecommendation selectById(@Param("id") Long id);

    /**
     * 查询某用户某场景下仍有效的推荐。
     */
    List<UserRecommendation> selectActiveByUserScene(@Param("userId") Long userId,
                                                     @Param("scene") String scene,
                                                     @Param("now") LocalDateTime now);

    int insert(UserRecommendation rec);

    int updateAccepted(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("taskId") Long taskId);

    int updateDismissed(@Param("id") Long id,
                        @Param("userId") Long userId);
}
