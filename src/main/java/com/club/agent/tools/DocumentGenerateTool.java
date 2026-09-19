package com.club.agent.tools;

import com.club.entity.Activity;
import com.club.entity.Club;
import com.club.service.ActivityService;
import com.club.service.ClubService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文案生成技能：活动方案文案 + 活动总结
 * 权限：仅社团负责人/管理员可调用（Tool 内部做权限校验，防越权）
 * 注：Tool 只做轻量编排，复杂查询全部复用已有 Service，不直接操作 Mapper 之外的数据层
 */
@Component
public class DocumentGenerateTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthUtil authUtil;
    private final ClubService clubService;
    private final ActivityService activityService;

    public DocumentGenerateTool(AuthUtil authUtil, ClubService clubService,
                                ActivityService activityService) {
        this.authUtil = authUtil;
        this.clubService = clubService;
        this.activityService = activityService;
    }

    /**
     * 生成活动方案文案（新建活动页「AI生成文案」按钮）
     */
    @Tool(description = "根据活动主题和社团信息生成一份完整的校园活动方案文案，包含活动主题、活动背景、活动流程、宣传口号四个部分")
    public String generateActivityPlan(
            @ToolParam(description = "活动主题") String theme,
            @ToolParam(description = "发起社团名称") String clubName,
            @ToolParam(description = "补充要求，可为空字符串", required = false) String extraInput) {
        checkLeaderPermission();
        if (theme == null || theme.isBlank()) {
            throw new BizException("活动主题不能为空");
        }
        String club = clubName == null || clubName.isBlank() ? "本校社团" : clubName;
        return String.format("""
                【活动主题】%s

                【活动背景】
                为丰富校园文化生活，%s拟举办"%s"主题活动。本活动面向全校同学开放报名，旨在搭建展示与交流平台，增强社团凝聚力与影响力。

                【活动流程】
                1. 前期宣传：通过社团公众号、海报与班级群进行一周预热宣传；
                2. 线上报名：开放线上报名通道，按名额录取；
                3. 活动当天：签到入场 -> 主体环节 -> 互动交流 -> 合影留念；
                4. 后期总结：收集参与者反馈，整理影像资料并归档。

                【宣传口号】
                以热爱相聚，因%s而不同——%s等你来！%s""",
                theme, club, theme, theme, club,
                extraInput == null || extraInput.isBlank() ? "" : "\n\n【特别说明】" + extraInput);
    }

    /**
     * 生成活动总结（活动结束后调用，基于真实报名/签到数据）
     */
    @Tool(description = "根据活动ID生成活动总结文案，自动统计报名与签到数据，包含活动概况、数据统计、亮点与建议四个部分")
    public String generateActivitySummary(
            @ToolParam(description = "活动ID") Long activityId,
            @ToolParam(description = "补充要求，可为空字符串", required = false) String extraInput) {
        checkLeaderPermission();
        Activity activity = activityService.getById(activityId);
        if (activity == null) {
            throw new BizException("活动不存在");
        }
        // 数据权限：发起社团负责人才能生成该活动总结
        authUtil.checkClubLeader(activity.getClubId());

        Club club = clubService.getById(activity.getClubId());
        // 复用已有 Service 查询报名数据（自动经过数据权限过滤）
        List<com.club.dto.SignupVO> signups = activityService.listSignups(activityId);
        long checkedCount = signups.stream().filter(s -> s.getChecked() != null && s.getChecked() == 1).count();
        String time = (activity.getStartTime() == null ? "" : activity.getStartTime().format(FMT));

        return String.format("""
                【活动总结】%s

                【活动概况】
                该活动由%s发起，于%s%s举行，目前已顺利结束。活动整体组织有序，参与者反馈良好。

                【数据统计】
                - 报名人数：%d 人
                - 签到人数：%d 人
                - 到场率：%s
                - 名额设置：%s

                【活动亮点】
                1. 活动主题契合社团定位，参与者积极性高；
                2. 现场流程执行顺畅，签到组织有序。

                【改进建议】
                1. 建议下期适当增加宣传预热时间，扩大报名基数；
                2. 可增加互动环节设计，提升现场参与感。%s""",
                activity.getTitle(),
                club == null ? "发起社团" : club.getName(),
                time,
                activity.getLocation() == null ? "" : "在" + activity.getLocation(),
                signups.size(), checkedCount,
                signups.isEmpty() ? "0%" : Math.round(checkedCount * 100.0 / signups.size()) + "%",
                activity.getCapacity() == null || activity.getCapacity() == 0 ? "不限" : activity.getCapacity() + "人",
                extraInput == null || extraInput.isBlank() ? "" : "\n\n【补充说明】" + extraInput);
    }

    /**
     * 通知文案润色（负责人发站内消息前调用）
     * 支持三种风格：正式 / 亲切 / 紧急；核心约束是「贴合选定风格 + 保留原文关键信息（时间、地点、要求）」。
     *
     * @param draft     通知草稿原文
     * @param style     风格：正式 / 亲切 / 紧急（为空按「正式」处理）
     * @param extraInput 额外要求，可为空
     */
    @Tool(description = "把通知草稿改写成指定风格的通知文案，支持正式/亲切/紧急三种风格，必须保留原文的关键信息（时间、地点、参与要求等）")
    public String polishNotice(
            @ToolParam(description = "通知草稿原文") String draft,
            @ToolParam(description = "目标风格：正式 / 亲切 / 紧急，可为空字符串", required = false) String style,
            @ToolParam(description = "额外要求，可为空字符串", required = false) String extraInput) {
        checkLeaderPermission();
        if (draft == null || draft.isBlank()) {
            throw new BizException("通知草稿不能为空");
        }
        // 风格归一化：未指定或非预期值统一按「正式」处理，避免出现未定义分支
        String target = normalizeStyle(style);
        String body = draft.trim();

        String polished = switch (target) {
            case "亲切" -> String.format("""
                    【亲切版通知】

                    各位同学好呀～

                    %s

                    有任何疑问随时在群里喊一声，我们会尽快回复大家，感谢配合与支持！""", body);
            case "紧急" -> String.format("""
                    【紧急通知】请立即关注

                    %s

                    请注意：以上事项时间紧迫，请务必在规定时间内完成，逾期将影响后续安排。感谢理解与配合！""", body);
            default -> String.format("""
                    【通知】

                    各位同学：

                    %s

                    特此通知，请遵照执行。如有疑问，请联系本社团负责人。

                    校社团联合会
                    """, body);
        };
        if (extraInput != null && !extraInput.isBlank()) {
            polished = polished + "\n\n【补充说明】" + extraInput;
        }
        return polished;
    }

    /**
     * 联合活动方案生成（负责人创建联合活动前调用）
     * 会依据受邀社团的真实名称/标签生成「贴合实际」的分工建议，并给出时间线、宣传与应急预案。
     *
     * @param theme      联合活动主题
     * @param clubNames  受邀社团名称（逗号分隔，可为空）
     * @param extraInput 额外要求，可为空
     */
    @Tool(description = "根据联合活动主题和受邀社团列表生成联合活动策划方案，包含主题定位、各社团分工、筹备时间线、宣传方案与安全应急预案")
    public String generateJointPlan(
            @ToolParam(description = "联合活动主题") String theme,
            @ToolParam(description = "受邀社团名称，多个用逗号分隔，可为空字符串", required = false) String clubNames,
            @ToolParam(description = "额外要求，可为空字符串", required = false) String extraInput) {
        checkLeaderPermission();
        if (theme == null || theme.isBlank()) {
            throw new BizException("联合活动主题不能为空");
        }
        List<String> invited = parseClubNames(clubNames);
        String initiator = resolveInitiatorClubName();
        // 逐个社团生成「贴合实际」的分工：依据社团标签推断擅长方向
        StringBuilder division = new StringBuilder();
        int idx = 1;
        for (String name : invited) {
            division.append(String.format("%d. %s：%s%n", idx++, name, dutyFor(name)));
        }
        if (invited.isEmpty()) {
            division.append("1. （暂无受邀社团，请先选择受邀社团后再生成分工建议）\n");
        }

        return String.format("""
                【联合活动策划方案】%s

                【一、主题定位】
                本次活动由「%s」发起，联合%s共同举办，主题为"%s"。
                定位：通过跨社团协作，实现优势互补与资源整合，扩大活动覆盖面与校园影响力。

                【二、各社团分工】
                %s
                【三、筹备时间线】
                - T-14 天：发起社团发出邀请，各社团负责人确认参与（系统内确认后邀请即生效）；
                - T-10 天：召开线上筹备会，明确分工、预算与物资清单；
                - T-7 天：启动宣传预热（公众号推文 + 海报 + 班级群转发）；
                - T-3 天：完成场地与设备确认、志愿者排班、安全预案交底；
                - T-1 天：物料到位、流程彩排、签到码与名单核对；
                - 活动当天：按流程执行，设专人负责签到、秩序与应急；
                - T+2 天：素材归档、数据复盘、联合总结发布。

                【四、宣传方案】
                1. 多社团联合宣传：各社团公众号同步推送，形成矩阵式传播；
                2. 视觉统一：由擅长设计的社团统一出海报与横幅模板；
                3. 报名引导：活动大厅页面开放报名，按名额控制并提示报名截止时间；
                4. 二次传播：活动当天产出图文/短视频素材，用于后续招新宣传。

                【五、安全应急预案】
                1. 明确安全负责人（联合活动为必填项），现场设置应急联络人；
                2. 提前确认场地承载量与疏散路线，配备基础急救物资；
                3. 若遇极端天气或突发状况，由发起社团负责人决定延期/转线上，并第一时间通知全部参与社团；
                4. 建立现场沟通群，出现情况逐级上报，确保信息同步。%s""",
                theme, initiator,
                invited.isEmpty() ? "受邀社团" : String.join("、", invited),
                theme,
                division,
                extraInput == null || extraInput.isBlank() ? "" : "\n\n【补充要求】" + extraInput);
    }

    /** 风格归一化：非「正式/亲切/紧急」的输入统一按「正式」处理 */
    private String normalizeStyle(String style) {
        if (style == null || style.isBlank()) {
            return "正式";
        }
        String s = style.trim();
        if (s.contains("亲切") || s.contains("轻松") || s.contains("活泼")) {
            return "亲切";
        }
        if (s.contains("紧急") || s.contains("急") || s.contains("加急")) {
            return "紧急";
        }
        return "正式";
    }

    /** 按社团名称推断分工方向（结合名字中的关键词，给出贴合实际的建议） */
    private String dutyFor(String clubName) {
        String n = clubName == null ? "" : clubName;
        if (n.contains("摄影") || n.contains("影像")) {
            return "负责活动影像记录（拍照/摄像）与宣传素材输出";
        }
        if (n.contains("志愿") || n.contains("公益")) {
            return "负责现场秩序维护、签到引导与志愿服务";
        }
        if (n.contains("计算机") || n.contains("科技") || n.contains("编程")) {
            return "负责线上报名系统支持、活动数据统计与技术支持";
        }
        if (n.contains("文艺") || n.contains("音乐") || n.contains("舞蹈") || n.contains("话剧")) {
            return "负责活动主体节目编排与现场演出环节";
        }
        if (n.contains("设计") || n.contains("美术") || n.contains("书画")) {
            return "负责海报、横幅等视觉物料设计";
        }
        if (n.contains("外联") || n.contains("公关")) {
            return "负责对外联络、赞助洽谈与嘉宾邀请";
        }
        return "协同参与活动策划与现场执行，按筹备会分工承担相应环节";
    }

    /** 逗号分隔的社团名称 -> 列表（去空、去重保序） */
    private List<String> parseClubNames(String clubNames) {
        if (clubNames == null || clubNames.isBlank()) {
            return List.of();
        }
        return List.of(clubNames.split("[,，、]")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** 当前负责人管理的社团名称（用于方案中标注发起方） */
    private String resolveInitiatorClubName() {
        try {
            Long clubId = authUtil.requireManagedClubId();
            Club club = clubService.getById(clubId);
            return club == null ? "本社团" : club.getName();
        } catch (BizException e) {
            // 管理员无管理社团：用泛化名称，不影响方案生成
            return "本校社团";
        }
    }

    /** Tool 权限校验：仅社团负责人/管理员（防越权） */
    private void checkLeaderPermission() {
        if (!authUtil.isLeader() && !authUtil.isAdmin()) {
            throw new BizException(403, "无权限：文案生成技能仅面向社团负责人开放");
        }
    }
}
