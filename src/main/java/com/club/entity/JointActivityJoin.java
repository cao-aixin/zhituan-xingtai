package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 联合活动参与社团中间表
 */
@Data
@TableName("joint_activity_join")
public class JointActivityJoin {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long clubId;
    /** 确认状态：0待确认 1同意 2拒绝（见 JointJoinStatus 枚举） */
    private Integer joinStatus;
    /** 拒绝理由（拒绝时必填） */
    private String refuseReason;
    private LocalDateTime createTime;
}
