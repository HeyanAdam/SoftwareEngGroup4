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

    /** 内置：ADMIN / STUDENT / KB_ADMIN / DATA_VIEWER，见 RoleConstants */
    private String roleCode;

    private String roleName;

    private String description;

    private Integer status;

    private LocalDateTime createTime;
}
