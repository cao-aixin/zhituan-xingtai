package com.club.dto;

import lombok.Data;

/** 用户管理入参（管理员新增用户） */
@Data
public class UserDTO {

    private Long id;
    private String studentNo;
    private String name;
    private String phone;
    /** 角色ID（sys_role.id） */
    private Long roleId;
}
