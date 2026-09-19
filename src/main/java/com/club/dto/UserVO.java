package com.club.dto;

import lombok.Data;

import java.util.List;

/** 登录/当前用户信息出参 */
@Data
public class UserVO {

    private Long id;
    private String studentNo;
    private String name;
    private String phone;
    /** 角色：管理员 / 社团发起负责人 / 社团参与负责人 / 普通学生 */
    private List<String> roleCodes;
    private List<String> roleNames;
    /** 登录token（登录时返回） */
    private String token;
    /** 管理的社团ID（负责人才有值，便于前端判断） */
    private Long managedClubId;
    /** 是否开启AI个性化推荐 */
    private Integer aiRecommend;
}
