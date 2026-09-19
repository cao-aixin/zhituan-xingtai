package com.club.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 智能体调用入参（统一入口 /api/ai/generate）
 * 说明：新增字段均为「可选」，不传时保持原有技能行为，向后兼容。
 */
@Data
public class AiRequestDTO {

    /** 技能名称：ACTIVITY_PLAN / ACTIVITY_SUMMARY / CLUB_ANALYSE / ACTIVITY_RECOMMEND
     *          / RISK_INSPECT / NOTICE_POLISH / JOINT_PLAN / ACTIVITY_QA */
    private String skill;
    /** 业务ID：活动ID（文案/总结/风险巡检场景） */
    private Long activityId;
    /** 业务ID：社团ID（运营分析场景） */
    private Long clubId;
    /** 用户补充输入（如：主题、要求、备注、学生提问） */
    private String extraInput;

    // ==================== 扩展字段（新技能使用，均可选） ====================

    /** 待处理文本正文：通知润色场景传「通知草稿原文」 */
    private String text;
    /** 风格偏好：通知润色场景传「正式 / 亲切 / 紧急」，为空时按「正式」处理 */
    private String style;
    /** 受邀社团ID列表：联合活动方案场景传受邀社团 */
    private List<Long> clubIds;
}
