package com.club.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 资料库分类枚举（沿用旧版 zhituan-system 分类体系）
 */
@Getter
@AllArgsConstructor
public enum ResourceCategory {

    /** 活动策划案 */
    PLAN("策划"),
    /** 活动总结 */
    SUMMARY("总结"),
    /** 活动照片 */
    PHOTO("照片"),
    /** 经费预算 */
    BUDGET("预算"),
    /** 其他资料 */
    OTHER("其他");

    private final String desc;

    /** 校验分类值是否合法（上传接口入参校验用） */
    public static boolean isValid(String value) {
        return Arrays.stream(values()).anyMatch(c -> c.desc.equals(value));
    }
}
