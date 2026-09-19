package com.club.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 站内消息类型枚举：区分待办任务与普通通知
 */
@Getter
@AllArgsConstructor
public enum MessageType {

    /** 待办任务（需要用户处理，如：确认联合活动、审批活动） */
    TODO(0, "待办任务"),
    /** 普通通知（仅告知，如：报名成功、审批结果） */
    NOTICE(1, "普通通知");

    private final Integer code;
    private final String desc;
}
