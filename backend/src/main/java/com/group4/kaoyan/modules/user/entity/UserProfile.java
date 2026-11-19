package com.group4.kaoyan.modules.user.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户画像（user_profile），一个用户一条。
 * 原生 MyBatis 实体：不继承 MP 基类，不加 @TableName / @TableId。
 */
@Data
public class UserProfile implements TimedEntity {

    private Long id;
    private Long userId;
    /** 性别：1 男 / 2 女 / 0 未填 */
    private Integer gender;
    private String major;
    private String grade;
    /** 基础水平：1 零基础 / 2 一般 / 3 较好 / 4 优秀 */
    private Integer baseLevel;
    private Integer weeklyStudyMinutes;
    /** JSON 列：薄弱点，存 List&lt;String&gt; 或对象 */
    private Object weakPointsJson;
    /** JSON 列：扩展画像，键值自由 */
    private Object profileJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
