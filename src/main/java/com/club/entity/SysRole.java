package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色表：学校管理员 / 社团发起负责人 / 社团参与负责人 / 普通学生
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色编码（见 RoleCode 枚举） */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
}
