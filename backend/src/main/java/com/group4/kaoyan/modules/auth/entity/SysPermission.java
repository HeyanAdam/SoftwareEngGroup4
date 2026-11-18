package com.group4.kaoyan.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** kb:view / doc:upload / admin:user:list ... */
    private String permCode;

    private String permName;

    /** MENU / BUTTON / API */
    private String permType;

    private Long parentId;

    private String path;

    private Integer sort;
}
