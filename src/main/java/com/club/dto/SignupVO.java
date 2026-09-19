package com.club.dto;

import lombok.Data;

/** 活动报名记录出参（数据权限过滤后返回） */
@Data
public class SignupVO {

    private Long id;
    private Long activityId;
    private Long userId;
    /** 学生姓名 */
    private String userName;
    /** 学号 */
    private String studentNo;
    /** 所属社团名称（学生可能属于多个社团，取报名时所属） */
    private String clubName;
    /** 是否已签到：0否 1是 */
    private Integer checked;
}
