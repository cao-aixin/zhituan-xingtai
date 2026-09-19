package com.club.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 联合活动受邀社团确认入参 */
@Data
public class JointConfirmDTO {

    /** 活动ID */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;
    /** 是否同意：true同意 false拒绝 */
    private Boolean agree;
    /** 拒绝理由（拒绝时必填） */
    private String refuseReason;
}
