package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户表
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 学号（统一登录账号，admin 为管理员账号） */
    private String studentNo;
    /** 姓名 */
    private String name;
    /** 手机号 */
    private String phone;
    /** 密码（MD5） */
    private String password;
    /** 是否开启AI个性化推荐 1开 0关 */
    private Integer aiRecommend;
    private LocalDateTime createTime;
}
