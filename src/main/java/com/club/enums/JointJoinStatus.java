package com.club.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 联合活动受邀社团确认状态枚举
 */
@Getter
@AllArgsConstructor
public enum JointJoinStatus {

    /** 待确认 */
    WAIT_CONFIRM(0, "待确认"),
    /** 已同意参与 */
    AGREED(1, "已同意"),
    /** 已拒绝参与 */
    REFUSED(2, "已拒绝");

    private final Integer code;
    private final String desc;

    public static JointJoinStatus of(Integer code) {
        for (JointJoinStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知联合确认状态: " + code);
    }
}
