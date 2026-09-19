package com.club.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 活动创建/编辑入参（普通活动与联合活动共用） */
@Data
public class ActivityDTO {

    private Long id;
    /** 活动标题 */
    @NotBlank(message = "活动标题不能为空")
    private String title;
    /** 是否联合活动：0普通 1联合 */
    private Integer isJoint;
    /** 活动地点 */
    private String location;
    /** 开始时间 yyyy-MM-dd HH:mm:ss */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    /** 名额（0不限） */
    private Integer capacity;
    /** 活动介绍/文案（AI生成后由用户确认回填） */
    private String intro;
    /** 安全负责人（联合活动必填） */
    private String safetyOfficer;
    /** 受邀社团ID列表（联合活动必填） */
    private List<Long> inviteClubIds;
}
