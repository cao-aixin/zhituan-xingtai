package com.club.enums;

import com.club.util.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 智能体技能名称枚举
 */
@Getter
@AllArgsConstructor
public enum AiSkillName {

    /** 活动方案/文案生成 */
    ACTIVITY_PLAN("ACTIVITY_PLAN", "活动文案生成"),
    /** 活动总结生成（活动结束后） */
    ACTIVITY_SUMMARY("ACTIVITY_SUMMARY", "活动总结生成"),
    /** 社团运营分析（统计页） */
    CLUB_ANALYSE("CLUB_ANALYSE", "社团运营分析"),
    /** 个性化活动推荐（学生首页） */
    ACTIVITY_RECOMMEND("ACTIVITY_RECOMMEND", "个性化活动推荐"),

    // ==================== 以下为第一批扩展技能 ====================

    /** 活动风险巡检（管理员审批页，仅对待审批活动，输出风险清单与处置建议） */
    RISK_INSPECT("RISK_INSPECT", "活动风险巡检"),
    /** 通知文案润色（负责人发站内消息前，按风格改写草稿并保留原意） */
    NOTICE_POLISH("NOTICE_POLISH", "通知文案润色"),
    /** 联合活动方案生成（负责人创建联合活动前，按主题+受邀社团生成协作策划） */
    JOINT_PLAN("JOINT_PLAN", "联合活动方案"),
    /** 活动问答助手（学生端对话式，基于本人可见的真实活动数据作答） */
    ACTIVITY_QA("ACTIVITY_QA", "活动问答助手");

    private final String code;
    private final String desc;

    public static AiSkillName of(String code) {
        for (AiSkillName s : values()) {
            if (s.code.equalsIgnoreCase(code)) {
                return s;
            }
        }
        // 非法技能属于「客户端入参错误」，必须抛 BizException(400) 走业务码返回。
        // 若沿用 IllegalArgumentException 会被 GlobalExceptionHandler 兜底成 500「系统繁忙」，
        // 前端无法区分「自己传错了技能名」还是「服务端故障」，API 契约失真。
        throw new BizException(400, "未知AI技能: " + code);
    }
}
