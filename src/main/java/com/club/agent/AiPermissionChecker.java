package com.club.agent;

import com.club.dto.AiRequestDTO;
import com.club.entity.Activity;
import com.club.enums.ActivityStatus;
import com.club.enums.AiSkillName;
import com.club.service.ActivityService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.stereotype.Component;

/**
 * AI 技能前置校验器（权限 + 参数/状态 双层校验）
 *
 * 背景：配置真实 api-key 后，@Tool 内部抛出的 BizException 会被 Spring AI 的
 * ToolExecutionExceptionProcessor 捕获并转成错误文本喂回大模型，模型据此组织出
 * 「无权访问 / 参数有误」的文案后仍按成功返回 200，导致 API 契约失真
 * （前端无法区分成功与被拒，ai_generate_record 还会错误落成 call_status=1）。
 * 因此在调用大模型之前先做一次与 Tool 内部完全一致的前置校验，不通过直接抛业务码。
 *
 * 校验分层（两类规则都在「调模型之前」完成，失败经 AgentService.invoke 的
 * catch BizException 落一条 call_status=0 失败记录后原样抛出）：
 * 1. 权限校验：角色 / 数据权限（与各 Tool 内部 checkXxx 口径一致，抛 403）；
 * 2. 参数与状态校验：入参非空、实体存在性、业务状态约束（抛 400，与 Tool 内文案逐字一致）。
 *
 * 注意：Tool 内部的校验全部保留不动——降级路径（localFallback）直调 Tool 方法，依赖它校验；
 * 同时也是防御性兜底，确保即使不经本类也能拦住非法请求。
 */
@Component
public class AiPermissionChecker {

    private final AuthUtil authUtil;
    /** 用于参数/状态前置校验（活动是否存在、状态是否允许该技能） */
    private final ActivityService activityService;

    public AiPermissionChecker(AuthUtil authUtil, ActivityService activityService) {
        this.authUtil = authUtil;
        this.activityService = activityService;
    }

    /**
     * 按技能做「权限 + 参数/状态」前置校验，规则与各 Tool 内部保持一致
     *
     * @param skill 技能名称
     * @param dto   请求参数（用于取 clubId / activityId / text）
     */
    public void check(AiSkillName skill, AiRequestDTO dto) {
        switch (skill) {
            // 活动方案 / 活动总结：仅社团负责人或学校管理员（对应 DocumentGenerateTool.checkLeaderPermission）
            case ACTIVITY_PLAN, ACTIVITY_SUMMARY -> {
                if (!authUtil.isLeader() && !authUtil.isAdmin()) {
                    throw new BizException(403, "无权限：文案生成技能仅面向社团负责人开放");
                }
            }
            // 运营分析：必须是目标社团的负责人或管理员（对应 ClubAnalyseTool 的 authUtil.checkClubLeader）
            case CLUB_ANALYSE -> {
                Long clubId = dto.getClubId() != null ? dto.getClubId() : managedClubIdOrNull();
                if (clubId == null) {
                    throw new BizException(403, "无权限：您没有管理的社团");
                }
                authUtil.checkClubLeader(clubId);
            }
            // 活动推荐：登录即可，不做额外限制（对应 ActivityRecommendTool）
            case ACTIVITY_RECOMMEND -> {
                // no-op：Sa-Token 拦截器已保证登录
            }
            // 活动风险巡检：仅学校管理员（对应 RiskInspectTool.inspectActivityRisk 内的 authUtil.checkAdmin）
            // 巡检结果服务于审批判断，学生/负责人无权触发，避免拿到审批线索
            case RISK_INSPECT -> {
                authUtil.checkAdmin();
                // 参数与状态前置校验：文案与 RiskInspectTool 内逐字一致。
                // 若不前置，AI 路径下 Tool 抛出的 BizException 会被 Spring AI 吞掉转成
                // 「状态不符」文案并以 200 成功返回，错误码与 call_status 双双失真。
                if (dto.getActivityId() == null) {
                    throw new BizException("活动ID不能为空");
                }
                Activity activity = activityService.getById(dto.getActivityId());
                if (activity == null) {
                    throw new BizException("活动不存在");
                }
                if (!ActivityStatus.WAIT_SCHOOL_APPROVE.getCode().equals(activity.getStatus())) {
                    throw new BizException("仅待校方审批的活动可做风险巡检，当前状态："
                            + ActivityStatus.of(activity.getStatus()).getDesc());
                }
            }
            // 通知润色 / 联合活动方案：负责人或管理员（对应 DocumentGenerateTool.checkLeaderPermission）
            // 与原有文案生成技能权限口径保持一致
            case NOTICE_POLISH, JOINT_PLAN -> {
                if (!authUtil.isLeader() && !authUtil.isAdmin()) {
                    throw new BizException(403, "无权限：文案生成技能仅面向社团负责人开放");
                }
                // 通知润色：草稿正文不能为空（与 DocumentGenerateTool.polishNotice 一致）
                // 注：JOINT_PLAN 的 theme 校验不前置——buildPrompt 已用「联合主题活动」兜底，
                // AI 路径不可能为空，保持现有行为不引入无谓分支。
                if (skill == AiSkillName.NOTICE_POLISH
                        && (dto.getText() == null || dto.getText().isBlank())) {
                    throw new BizException("通知草稿不能为空");
                }
            }
            // 活动问答助手：登录即可用（Sa-Token 拦截器已保证登录），
            // 数据层面由 ActivityQaTool 复用 Service 自动做数据权限过滤，回答只基于用户可见数据
            case ACTIVITY_QA -> {
                // no-op：登录用户均可问答，数据权限在 Tool 内部生效
            }
        }

        // 活动总结的「参数 + 数据权限」二次校验：
        // 单独放在 switch 之后，避免与 ACTIVITY_PLAN 共用分支时把 theme 类技能也卷进来。
        // 对应 DocumentGenerateTool.generateActivitySummary 的 activityId/存在性/checkClubLeader，
        // 其中 checkClubLeader 一并前置，防止「负责人查他人活动」的 403 被 Spring AI 吞成 200。
        if (skill == AiSkillName.ACTIVITY_SUMMARY) {
            if (dto.getActivityId() == null) {
                throw new BizException("活动ID不能为空");
            }
            Activity activity = activityService.getById(dto.getActivityId());
            if (activity == null) {
                throw new BizException("活动不存在");
            }
            authUtil.checkClubLeader(activity.getClubId());
        }
    }

    private Long managedClubIdOrNull() {
        try {
            return authUtil.requireManagedClubId();
        } catch (BizException e) {
            return null;
        }
    }
}
