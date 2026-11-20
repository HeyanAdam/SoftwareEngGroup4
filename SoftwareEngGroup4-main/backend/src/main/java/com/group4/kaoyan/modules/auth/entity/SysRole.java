package com.group4.kaoyan.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** STUDENT / CONTENT_OPERATOR / DATA_AUDITOR / SYSTEM_ADMIN */
    private String roleCode;

    private String roleName;

    private String description;

    private Integer status;

    private LocalDateTime createTime;
}
