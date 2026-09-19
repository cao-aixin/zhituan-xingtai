package com.club.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 社团状态枚举
 */
@Getter
@AllArgsConstructor
public enum ClubStatus {

    /** 待审核（申请中） */
    PENDING(0, "待审核"),
    /** 正常运行 */
    ACTIVE(1, "正常"),
    /** 已驳回 */
    REJECTED(2, "已驳回");

    private final Integer code;
    private final String desc;
}
