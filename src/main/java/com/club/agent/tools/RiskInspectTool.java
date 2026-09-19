package com.club.agent.tools;

import com.club.dto.ActivityVO;
import com.club.dto.SignupVO;
import com.club.entity.Activity;
import com.club.entity.Club;
import com.club.enums.ActivityStatus;
import com.club.service.ActivityService;
import com.club.service.ClubService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动风险巡检技能（管理员「活动审批」页「AI 风险巡检」按钮）
 *
 * 设计要点：
 * 1. 权限：仅学校管理员可调用（审批动作的辅助判断，学生/负责人无权触发）；
 * 2. 范围：只针对「待校方审批」状态的活动做巡检，非待审批活动直接报错，
 *    避免管理员对已进行/已结束活动重复巡检产生误导结论；
 * 3. 数据：全部复用已有 Service（ActivityService / ClubService）查询，Tool 内不直查 Mapper；
 * 4. 输出：结构化风险清单（时间冲突 / 安全 / 内容合规 / 预算与资源）+ 处置建议，
 *    但只作为审批参考，不改变审批结果，最终是否通过仍由管理员在页面上决定。
 */
@Component
public class RiskInspectTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 判定「时间冲突」的时间邻近阈值（小时）：同场地前后 2 小时内视为潜在冲突 */
    private static final long CONFLICT_HOURS = 2;

    /** 报名人数明显偏低的比例阈值：报名数 / 名额 < 0.1 时提示资源利用率风险 */
    private static final double LOW_UTILIZATION_RATIO = 0.1;

    private final AuthUtil authUtil;
    private final ActivityService activityService;
    private final ClubService clubService;

    public RiskInspectTool(AuthUtil authUtil, ActivityService activityService, ClubService clubService) {
        this.authUtil = authUtil;
        this.activityService = activityService;
        this.clubService = clubService;
    }

    /**
     * 对指定待审批活动执行风险巡检
     *
     * @param activityId 待审批活动ID
     * @param extraInput 管理员关注的额外巡检重点，可为空字符串
     * @return 风险巡检报告（纯文本，供前端弹窗预览）
     */
    @Tool(description = "对指定待校方审批的活动做风险巡检：检查时间冲突、安全风险、内容合规、预算与资源合理性，输出风险清单与处置建议")
    public String inspectActivityRisk(
            @ToolParam(description = "待校方审批的活动ID") Long activityId,
            @ToolParam(description = "管理员额外关注的巡检重点，可为空字符串", required = false) String extraInput) {
        // 功能权限：风险巡检服务于审批动作，仅管理员可用（防越权查看审批线索）
        authUtil.checkAdmin();
        if (activityId == null) {
            throw new BizException("活动ID不能为空");
        }
        Activity activity = activityService.getById(activityId);
        if (activity == null) {
            throw new BizException("活动不存在");
        }
        // 业务约束：仅对「待校方审批」的活动做巡检
        if (!ActivityStatus.WAIT_SCHOOL_APPROVE.getCode().equals(activity.getStatus())) {
            throw new BizException("仅待校方审批的活动可做风险巡检，当前状态："
                    + ActivityStatus.of(activity.getStatus()).getDesc());
        }

        Club initiator = clubService.getById(activity.getClubId());
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // ---------- 1. 检查项：时间设置合理性 ----------
        checkTimeSetting(activity, risks, suggestions);

        // ---------- 2. 检查项：时间/场地冲突 ----------
        checkScheduleConflict(activity, risks, suggestions);

        // ---------- 3. 检查项：安全风险 ----------
        checkSafety(activity, risks, suggestions);

        // ---------- 4. 检查项：内容合规 ----------
        checkContentCompliance(activity, initiator, risks, suggestions);

        // ---------- 5. 检查项：预算与资源合理性 ----------
        checkResource(activity, risks, suggestions);

        // ---------- 6. 检查项：联合活动受邀社团确认情况 ----------
        checkJointConfirmState(activity, risks, suggestions);

        return renderReport(activity, initiator, risks, suggestions, extraInput);
    }

    // ==================== 各检查项 ====================

    /** 时间设置：起止时间必须齐全且结束晚于开始 */
    private void checkTimeSetting(Activity a, List<String> risks, List<String> suggestions) {
        if (a.getStartTime() == null || a.getEndTime() == null) {
            risks.add("【高】活动起止时间不完整，无法评估时间合理性");
            suggestions.add("要求发起社团补齐活动开始/结束时间后重新提交审批。");
            return;
        }
        if (!a.getEndTime().isAfter(a.getStartTime())) {
            risks.add("【高】活动结束时间早于或等于开始时间，时间设置有误");
            suggestions.add("提醒负责人修正活动时间区间，避免签到/结束流程异常。");
            return;
        }
        long hours = Duration.between(a.getStartTime(), a.getEndTime()).toHours();
        if (hours > 8) {
            risks.add("【中】单场活动时长约 " + hours + " 小时，超出常规单场活动时长（8 小时）");
            suggestions.add("确认是否为全天候活动；如确需长时间举办，请补充安全值守与轮换安排。");
        }
        if (a.getStartTime().isBefore(LocalDateTime.now())) {
            risks.add("【中】活动开始时间早于当前时间，可能存在先办后批的风险");
            suggestions.add("核实活动实际排期，必要时要求负责人顺延时间后重新提交。");
        }
    }

    /** 时间/场地冲突：同场地在相近时间段内是否已有其他已通过活动 */
    private void checkScheduleConflict(Activity a, List<String> risks, List<String> suggestions) {
        if (a.getStartTime() == null || a.getEndTime() == null || a.getLocation() == null
                || a.getLocation().isBlank()) {
            return;
        }
        // 复用 Service 查询全部活动（管理员视角），本地比对场地与时间邻近度
        List<ActivityVO> all = activityService.listActivities(null, null);
        List<String> conflicts = new ArrayList<>();
        for (ActivityVO other : all) {
            if (other.getId() == null || other.getId().equals(a.getId())) {
                continue;
            }
            // 只与已进入执行阶段的活动比对（进行中/已结束），待审批的同类活动不算硬冲突
            if (!ActivityStatus.IN_PROGRESS.getCode().equals(other.getStatus())
                    && !ActivityStatus.FINISHED.getCode().equals(other.getStatus())) {
                continue;
            }
            if (other.getLocation() == null || !other.getLocation().equals(a.getLocation())) {
                continue;
            }
            LocalDateTime os = parseTime(other.getStartTime());
            LocalDateTime oe = parseTime(other.getEndTime());
            if (os == null || oe == null) {
                continue;
            }
            // 时间区间重叠 或 前后间隔小于阈值 -> 视为潜在冲突
            boolean overlap = a.getStartTime().isBefore(oe) && a.getEndTime().isAfter(os);
            long gapMinutes = Math.min(
                    Math.abs(Duration.between(a.getEndTime(), os).toMinutes()),
                    Math.abs(Duration.between(a.getStartTime(), oe).toMinutes()));
            if (overlap || gapMinutes < CONFLICT_HOURS * 60) {
                conflicts.add("《" + other.getTitle() + "》(" + other.getStartTime() + " ~ " + other.getEndTime() + ")");
            }
        }
        if (!conflicts.isEmpty()) {
            risks.add("【高】同一场地「" + a.getLocation() + "」存在潜在时间冲突：" + String.join("、", conflicts));
            suggestions.add("与相关活动负责人协调场地使用时段，或更换活动场地后重新审批。");
        }
    }

    /** 安全风险：安全负责人、活动规模、场地类型综合判断 */
    private void checkSafety(Activity a, List<String> risks, List<String> suggestions) {
        if (a.getSafetyOfficer() == null || a.getSafetyOfficer().isBlank()) {
            if (a.getIsJoint() != null && a.getIsJoint() == 1) {
                risks.add("【高】联合活动未填写安全负责人（联合活动为强制项）");
                suggestions.add("驳回并要求补充安全负责人后再提交审批。");
            } else {
                risks.add("【低】未填写安全负责人，大型活动建议明确现场安全责任人");
                suggestions.add("建议负责人补充安全负责人姓名，便于突发情况责任到人。");
            }
        }
        int capacity = a.getCapacity() == null ? 0 : a.getCapacity();
        if (capacity >= 200) {
            risks.add("【高】活动名额 " + capacity + " 人，属大型聚集活动，安全压力较大");
            suggestions.add("要求提交安全预案（疏散路线、医疗点、志愿者配比），必要时报校保卫处备案。");
        } else if (capacity >= 100) {
            risks.add("【中】活动名额 " + capacity + " 人，需关注现场秩序与场地承载量");
            suggestions.add("提醒负责人安排足够志愿者维持秩序，核对场地可容纳人数。");
        }
        String location = a.getLocation() == null ? "" : a.getLocation();
        // 户外/高强度类场地的关键词提示
        if (location.contains("操场") || location.contains("户外") || location.contains("广场")
                || location.contains("山") || location.contains("湖")) {
            risks.add("【中】活动地点「" + location + "」为户外/开放场地，受天气与人员管控影响较大");
            suggestions.add("补充雨天/极端天气备选方案与现场围挡、警示安排。");
        }
    }

    /** 内容合规：标题/介绍是否触及敏感、商业或危险内容 */
    private void checkContentCompliance(Activity a, Club club, List<String> risks,
                                        List<String> suggestions) {
        String text = ((a.getTitle() == null ? "" : a.getTitle()) + " "
                + (a.getIntro() == null ? "" : a.getIntro())).toLowerCase();
        List<String> sensitive = List.of("赌博", "彩票", "借贷", "网贷", "传销", "代刷", "代考", "兼职刷单",
                "烟", "酒", "纹身", "蹦极", "极限", "骑行进藏", "私自", "校外过夜");
        List<String> hits = sensitive.stream().filter(text::contains).toList();
        if (!hits.isEmpty()) {
            risks.add("【高】活动文案命中需重点核验内容关键词：" + String.join("、", hits));
            suggestions.add("人工核验活动实质内容与合规性，必要时要求修改文案或不予通过。");
        }
        if (a.getIntro() == null || a.getIntro().isBlank()) {
            risks.add("【中】活动介绍为空，无法评估内容合规与活动实质");
            suggestions.add("要求负责人补充活动介绍与流程说明后重新提交。");
        } else if (a.getIntro().length() < 30) {
            risks.add("【低】活动介绍过于简略（" + a.getIntro().length() + " 字），信息不足以判断活动内容");
            suggestions.add("建议补充活动背景、流程与参与对象说明。");
        }
        if (club == null) {
            risks.add("【高】发起社团不存在或已被删除，数据异常");
            suggestions.add("核实发起社团数据，暂缓审批并联系系统管理员排查。");
        } else if (club.getStatus() == null || club.getStatus() != 1) {
            risks.add("【高】发起社团当前状态非「正常运营」，不具备办活动资质");
            suggestions.add("先处理社团状态问题，再评估该活动是否继续审批。");
        }
    }

    /** 预算/资源合理性：名额与实际报名、活动规模与社团体量的匹配度 */
    private void checkResource(Activity a, List<String> risks, List<String> suggestions) {
        // 复用已有 Service 查询报名数据（自动经过数据权限过滤，管理员可见全量）
        List<SignupVO> signups = activityService.listSignups(a.getId());
        int capacity = a.getCapacity() == null ? 0 : a.getCapacity();
        if (capacity > 0) {
            double ratio = signups.size() * 1.0 / capacity;
            if (signups.size() > capacity) {
                risks.add("【中】报名人数 " + signups.size() + " 已超名额 " + capacity + "，存在超员风险");
                suggestions.add("核实名额设置与场地承载量，必要时上调名额或进行报名分流。");
            } else if (ratio < LOW_UTILIZATION_RATIO && signups.size() < 5) {
                risks.add("【低】报名仅 " + signups.size() + " 人 / 名额 " + capacity
                        + " 人，资源利用率偏低，宣传或时间安排可能存在问题");
                suggestions.add("建议负责人加强前期宣传，或调整活动时间与名额设置。");
            }
        }
        if (capacity == 0 || a.getCapacity() == null) {
            risks.add("【低】活动名额设为「不限」，缺少人流上限控制");
            suggestions.add("建议根据场地承载量设置合理名额上限，便于安全与签到管理。");
        }
    }

    /** 联合活动：受邀社团确认状态是否齐备 */
    private void checkJointConfirmState(Activity a, List<String> risks, List<String> suggestions) {
        if (a.getIsJoint() == null || a.getIsJoint() != 1) {
            return;
        }
        ActivityVO vo = activityService.detail(a.getId());
        if (vo.getJointJoins() == null || vo.getJointJoins().isEmpty()) {
            risks.add("【高】联合活动未记录任何受邀社团，数据异常");
            suggestions.add("核验联合活动受邀名单，异常时不予审批。");
            return;
        }
        List<String> pending = vo.getJointJoins().stream()
                .filter(j -> Integer.valueOf(0).equals(j.getJoinStatus()))
                .map(ActivityVO.JointJoinVO::getClubName).toList();
        List<String> refused = vo.getJointJoins().stream()
                .filter(j -> Integer.valueOf(2).equals(j.getJoinStatus()))
                .map(ActivityVO.JointJoinVO::getClubName).toList();
        if (!pending.isEmpty()) {
            risks.add("【高】以下受邀社团尚未确认参与：" + String.join("、", pending));
            suggestions.add("待全部受邀社团确认后再审批，避免活动临时变更或取消。");
        }
        if (!refused.isEmpty()) {
            risks.add("【中】以下受邀社团已拒绝参与：" + String.join("、", refused));
            suggestions.add("确认拒绝后方案是否仍成立，必要时要求发起社团调整分工或受邀名单。");
        }
    }

    // ==================== 报告渲染 ====================

    /** 渲染巡检报告：风险等级汇总 + 明细 + 处置建议 */
    private String renderReport(Activity a, Club club, List<String> risks, List<String> suggestions,
                                String extraInput) {
        long high = risks.stream().filter(r -> r.startsWith("【高】")).count();
        long mid = risks.stream().filter(r -> r.startsWith("【中】")).count();
        long low = risks.stream().filter(r -> r.startsWith("【低】")).count();
        String level = high > 0 ? "高风险（建议谨慎审批）"
                : mid > 0 ? "中风险（建议补充材料后审批）"
                : low > 0 ? "低风险（可正常审批）" : "未发现明显风险（可正常审批）";

        StringBuilder sb = new StringBuilder();
        sb.append("【AI 活动风险巡检报告】\n\n");
        sb.append("【巡检对象】").append(a.getTitle()).append("\n");
        sb.append("【发起社团】").append(club == null ? "未知社团" : club.getName()).append("\n");
        sb.append("【活动时间】").append(a.getStartTime() == null ? "未填写" : a.getStartTime().format(FMT))
                .append(" 至 ").append(a.getEndTime() == null ? "未填写" : a.getEndTime().format(FMT)).append("\n");
        sb.append("【活动地点】").append(a.getLocation() == null ? "未填写" : a.getLocation()).append("\n");
        sb.append("【风险等级】").append(level)
                .append("（高 ").append(high).append(" 项 / 中 ").append(mid).append(" 项 / 低 ").append(low).append(" 项）\n\n");

        sb.append("【风险清单】\n");
        if (risks.isEmpty()) {
            sb.append("经四维校验（时间冲突 / 安全 / 内容合规 / 预算资源），暂未发现明显风险点。\n");
        } else {
            int idx = 1;
            for (String r : risks) {
                sb.append(idx++).append(". ").append(r).append("\n");
            }
        }

        sb.append("\n【处置建议】\n");
        if (suggestions.isEmpty()) {
            sb.append("1. 可正常进入审批流程，注意留存活动安全与内容材料备查。\n");
        } else {
            int idx = 1;
            for (String s : suggestions) {
                sb.append(idx++).append(". ").append(s).append("\n");
            }
        }

        sb.append("\n【结论说明】\n本报告由 AI 基于系统内真实数据生成，仅作为审批参考，不改变审批结果，");
        sb.append("最终是否通过仍由管理员决定。");
        if (extraInput != null && !extraInput.isBlank()) {
            sb.append("\n\n【管理员关注重点】").append(extraInput);
        }
        return sb.toString();
    }

    /** 把 Service 返回的字符串时间（yyyy-MM-dd HH:mm:ss）解析为 LocalDateTime，解析失败返回 null */
    private LocalDateTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time.trim().replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
