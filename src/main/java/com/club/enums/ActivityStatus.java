package com.club.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 活动状态枚举（数字状态机，禁止魔法数字）
 */
@Getter
@AllArgsConstructor
public enum ActivityStatus {

    /** 草稿 */
    DRAFT(0, "草稿"),
    /** 待社团确认（仅联合活动） */
    WAIT_CLUB_CONFIRM(1, "待社团确认"),
    /** 待校方审批 */
    WAIT_SCHOOL_APPROVE(2, "待校方审批"),
    /** 进行中（审批通过） */
    IN_PROGRESS(3, "进行中"),
    /** 已结束 */
    FINISHED(4, "已结束"),
    /** 已驳回 */
    REJECTED(5, "已驳回");

    private final Integer code;
    private final String desc;

    /** 按 code 找枚举 */
    public static ActivityStatus of(Integer code) {
        for (ActivityStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知活动状态: " + code);
    }
}
