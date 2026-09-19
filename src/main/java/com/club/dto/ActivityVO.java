package com.club.dto;

import lombok.Data;

/** 活动出参（含发起社团名与联合确认状态列表） */
@Data
public class ActivityVO {

    private Long id;
    private String title;
    /** 0普通 1联合 */
    private Integer isJoint;
    private Long clubId;
    /** 发起社团名称 */
    private String clubName;
    private String location;
    private String startTime;
    private String endTime;
    private Integer capacity;
    private String intro;
    private String safetyOfficer;
    /** 状态码：0草稿 1待社团确认 2待校方审批 3进行中 4已结束 5已驳回 */
    private Integer status;
    private String statusDesc;
    private String rejectReason;
    /** 签到码（仅发起负责人/管理员可见） */
    private String checkinCode;
    private String summary;
    private Long creatorId;
    /** 联合活动受邀社团确认状态 */
    private java.util.List<JointJoinVO> jointJoins;
    /** 当前用户是否已报名 */
    private Boolean signed;

    /** 联合活动确认状态出参 */
    @Data
    public static class JointJoinVO {
        private Long id;
        private Long clubId;
        private String clubName;
        /** 0待确认 1同意 2拒绝 */
        private Integer joinStatus;
        private String joinStatusDesc;
        private String refuseReason;
    }
}
