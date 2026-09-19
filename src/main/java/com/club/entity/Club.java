package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团表
 */
@Data
@TableName("club")
public class Club {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 社团名称 */
    private String name;
    /** 社团简介 */
    private String intro;
    /** 标签（逗号分隔，如：科技,编程） */
    private String tags;
    /** 状态：0待审核 1正常 2已驳回（见 ClubStatus 枚举） */
    private Integer status;
    /** 驳回理由 */
    private String rejectReason;
    /** 负责人用户ID */
    private Long leaderId;
    private LocalDateTime createTime;
}
