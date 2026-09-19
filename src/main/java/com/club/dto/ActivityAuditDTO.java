package com.club.dto;

import lombok.Data;

/** 活动审批入参（管理员） */
@Data
public class ActivityAuditDTO {

    /** 审批结果：true通过 false驳回 */
    private Boolean approved;
    /** 驳回理由（驳回必填） */
    private String rejectReason;
}
