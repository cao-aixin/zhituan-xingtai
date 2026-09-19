package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动主表（普通活动 / 联合活动共用，is_joint 区分）
 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 活动标题 */
    private String title;
    /** 0普通活动 1联合活动 */
    private Integer isJoint;
    /** 发起社团ID */
    private Long clubId;
    /** 活动地点 */
    private String location;
    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    /** 名额（0不限） */
    private Integer capacity;
    /** 活动介绍/文案 */
    private String intro;
    /** 安全负责人（联合活动必填） */
    private String safetyOfficer;
    /** 状态：0草稿 1待社团确认 2待校方审批 3进行中 4已结束 5已驳回（见 ActivityStatus 枚举） */
    private Integer status;
    /** 驳回理由 */
    private String rejectReason;
    /** 签到码（审批通过后生成，学生填码签到） */
    private String checkinCode;
    /** 活动总结（结束后生成，AI生成内容经用户确认后回填） */
    private String summary;
    /** 创建人ID */
    private Long creatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
