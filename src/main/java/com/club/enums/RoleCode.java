package com.club.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 角色编码枚举（对应 sys_role.role_code）
 */
@Getter
@AllArgsConstructor
public enum RoleCode {

    /** 学校管理员 */
    ADMIN("ADMIN", "学校管理员"),
    /** 社团发起负责人（牵头创建活动的一方） */
    CLUB_LEADER("CLUB_LEADER", "社团发起负责人"),
    /** 社团参与负责人（受邀参与联合活动的一方） */
    CLUB_PARTICIPANT("CLUB_PARTICIPANT", "社团参与负责人"),
    /** 普通学生 */
    STUDENT("STUDENT", "普通学生");

    private final String code;
    private final String desc;

    /** 是否属于社团负责人层级（发起负责人 / 参与负责人，权限判断时等价处理） */
    public static boolean isLeaderRole(String roleCode) {
        return CLUB_LEADER.code.equals(roleCode) || CLUB_PARTICIPANT.code.equals(roleCode);
    }
}
