package com.club.agent.tools;

import com.club.dto.ActivityVO;
import com.club.dto.SignupVO;
import com.club.entity.Club;
import com.club.enums.ActivityStatus;
import com.club.enums.ClubStatus;
import com.club.enums.JointJoinStatus;
import com.club.enums.RoleCode;
import com.club.service.ActivityService;
import com.club.service.ClubMemberService;
import com.club.service.ClubService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 活动问答助手技能（右下角悬浮聊天面板，全角色可用）——【按角色差异化】
 *
 * 设计要点：
 * 1. 权限：登录即可用（Sa-Token 拦截器已保证登录），但**回答只能基于该用户有权限看到的数据**——
 *    活动数据统一通过 ActivityService.listActivities 获取，该方法内部已按角色做数据权限过滤
 *    （学生只能看到「进行中 / 已结束」的活动，负责人只能看自己发起或受邀的活动，管理员看全部）；
 * 2. 数据：Tool 内不直查 Mapper，全部复用已有 Service，保证与页面列表口径一致；
 * 3. 定位：Tool 负责「取真实数据 + 做基础筛选」，最终自然语言组织交给大模型；
 *    若大模型不可用，本地降级时直接返回本 Tool 的结构化内容，同样可用；
 *    因此**事实文本自身必须可读**：统一带【小节】标题，模型不可用时也能直接呈现；
 * 4. 多轮对话：不做会话记忆（与既有 Agent 设计一致），前端保留聊天记录展示。
 *
 * 【角色差异化的设计意图（本次改造核心）】
 * 同一个悬浮入口面对三类诉求完全不同的用户，若"一套学生视角走天下"会出现答非所问：
 *  - 学生关心「能参加什么」，给他「待审批/草稿」毫无意义还涉嫌数据越权；
 *  - 社团负责人关心「我的活动卡在哪一步、哪些待我处理」，学生那套"找活动"对他没用；
 *  - 团委老师关心「有哪些待我审批、全局态势如何」，与个人报名无关。
 * 因此这里按 **ADMIN > 负责人(CLUB_LEADER/CLUB_PARTICIPANT) > STUDENT** 的优先级分流，
 * 三条分支各自组织事实数据；一个用户只有一种角色，但按优先级判断更稳妥，
 * 即使未来出现多角色叠加，也只会落到权限最大的那一支，不会出现数据越权展示。
 */
@Component
public class ActivityQaTool {

    private final AuthUtil authUtil;
    private final ActivityService activityService;
    private final ClubService clubService;
    private final ClubMemberService clubMemberService;

    public ActivityQaTool(AuthUtil authUtil, ActivityService activityService,
                          ClubService clubService, ClubMemberService clubMemberService) {
        this.authUtil = authUtil;
        this.activityService = activityService;
        this.clubService = clubService;
        this.clubMemberService = clubMemberService;
    }

    /**
     * 回答用户关于活动的提问（入口方法，按角色分流）
     * 实现说明：把「该用户可见的真实活动 / 社团数据」检索出来作为事实依据返回，
     * 由大模型据此组织自然语言回答，避免编造不存在的活动。
     *
     * @param question 用户问题，如「有什么适合新手的活动？」
     * @return 基于真实数据的检索结果 + 供模型参考的事实清单
     */
    @Tool(description = "回答用户关于校园活动的提问。会根据当前登录用户的角色（学生 / 社团负责人 / 团委老师）" +
            "返回不同视角的真实数据：学生看到可参与活动与我的社团；负责人看到我管理的社团、活动状态分布、" +
            "待办事项与报名数据；团委老师看到待审批活动清单与全局概览。" +
            "数据均按权限过滤，不得编造数据")
    public String answerActivityQuestion(
            @ToolParam(description = "用户的提问内容") String question) {
        if (question == null || question.isBlank()) {
            throw new BizException("提问内容不能为空");
        }
        Long userId = authUtil.currentUserId();
        String q = question.trim();

        // 【角色分流】优先级 ADMIN > 负责人 > 学生。
        // 注意：这里只做"分流"，三支内部的数据检索口径完全一致（都走 ActivityService 的权限过滤），
        // 差别仅在"组织哪些小节"，因此不存在因分流而放宽数据权限的风险。
        if (authUtil.isAdmin()) {
            return buildAdminFacts(q, userId);
        }
        if (authUtil.isLeader()) {
            return buildLeaderFacts(q, userId);
        }
        return buildStudentFacts(q, userId);
    }

    // ==================================================================
    // 分支 A：学生（STUDENT）—— 保持原有能力，仅在展示上做小幅归类
    // ==================================================================

    /**
     * 学生视角事实数据：可参与活动（进行中且未报名，含剩余名额）+ 其他进行中活动 + 我的报名情况 + 我加入的社团
     */
    private String buildStudentFacts(String q, Long userId) {
        String lower = q.toLowerCase();

        // 该用户可见的全部活动（Service 内部已按角色做数据权限过滤）
        List<ActivityVO> visible = activityService.listActivities(null, null);
        // 可见的进行中活动
        List<ActivityVO> ongoing = visible.stream()
                .filter(a -> ActivityStatus.IN_PROGRESS.getCode().equals(a.getStatus()))
                .toList();
        // 该用户已报名的活动
        List<ActivityVO> signed = visible.stream()
                .filter(a -> Boolean.TRUE.equals(a.getSigned()))
                .toList();
        // 【关键】可参与候选 = 进行中 且 尚未报名 的活动：
        // 用户问「有什么活动可以参加」时，主体应是"还能报的名"，已报名的只作简要提及，
        // 否则模型会把已报名明细当候选展开，答非所问
        List<ActivityVO> joinable = ongoing.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getSigned()))
                .toList();
        // 该用户所在社团
        List<Long> myClubIds = clubMemberService.myClubIds(userId);
        List<String> myClubNames = myClubIds.stream()
                .map(clubService::getById)
                .filter(c -> c != null)
                .map(Club::getName)
                .toList();

        // 按提问特征做基础筛选（大模型据此组织答案，降级时也能直接返回可用内容）
        List<ActivityVO> candidates = filterByQuestion(joinable, lower);

        StringBuilder sb = new StringBuilder();
        sb.append("【用户提问】").append(q).append("\n\n");

        // ---------- 事实 1：可参与的活动候选（仅未报名的进行中活动） ----------
        if (candidates.isEmpty()) {
            sb.append("【可参与活动】当前没有与你提问条件匹配、且你尚未报名的进行中活动");
            if (!ongoing.isEmpty()) {
                sb.append("（你已报名全部 ").append(ongoing.size()).append(" 场进行中活动）");
            }
            sb.append("。\n");
        } else {
            sb.append("【可参与活动】（你尚未报名的进行中活动，共 ").append(candidates.size()).append(" 场）\n");
            int rank = 1;
            for (ActivityVO a : candidates.subList(0, Math.min(6, candidates.size()))) {
                sb.append(formatActivity(rank++, a));
            }
        }

        // ---------- 事实 2：其他进行中活动（未报名但未进候选，补充上下文） ----------
        if (joinable.size() > candidates.size()) {
            sb.append("\n【其他进行中活动】\n");
            joinable.stream()
                    .filter(a -> !candidates.contains(a))
                    .limit(6)
                    .forEach(a -> sb.append("- 《").append(a.getTitle()).append("》（")
                            .append(a.getClubName() == null ? "未知社团" : a.getClubName()).append("，")
                            .append(a.getStartTime() == null ? "时间待定" : a.getStartTime()).append("）\n"));
        }

        // ---------- 事实 3：我的报名情况 ----------
        sb.append("\n【我的报名情况】\n");
        if (signed.isEmpty()) {
            sb.append("你当前还没有报名任何活动。\n");
        } else {
            sb.append("你已报名 ").append(signed.size()).append(" 场活动：");
            sb.append(String.join("、", signed.stream().map(a -> "《" + a.getTitle() + "》").toList())).append("。\n");
        }

        // ---------- 事实 4：我的社团 ----------
        sb.append("\n【我加入的社团】\n");
        if (myClubNames.isEmpty()) {
            sb.append("你当前还没有加入任何社团，可到「社团列表」页面申请加入。\n");
        } else {
            sb.append("你已加入：").append(String.join("、", myClubNames))
                    .append("。可以优先关注这些社团发起的活动。\n");
        }

        sb.append("\n【回答要求】\n请基于以上真实数据，用自然、口语化的中文回答用户的问题；")
                .append("不要编造数据中不存在的活动；若数据为空，请如实说明并给出参与建议；")
                // 聚焦性要求：可参与活动才是回答主体，已报名活动不要展开明细，避免答非所问
                .append("「可参与活动」只包含用户尚未报名的进行中活动，是回答的主体；")
                .append("用户已报名的活动只在我的报名情况里简要提及，不要逐场展开；")
                .append("回答应直接回应用户所问，如问「可以参加什么」就优先推荐可参与活动并说明剩余名额。");
        if (ongoing.isEmpty() && signed.isEmpty()) {
            sb.append("；当前系统内暂无进行中的活动，可引导用户关注社团动态等待新活动上线");
        }
        sb.append("\n");
        return sb.toString();
    }

    // ==================================================================
    // 分支 B：社团负责人（CLUB_LEADER / CLUB_PARTICIPANT）—— 社团事务助手
    // ==================================================================

    /**
     * 负责人视角事实数据：我管理的社团 / 我的活动状态分布 / 待办事项 / 我社团活动报名签到数据 / 本人报名情况
     *
     * 数据权限说明：
     * - 活动列表走 listActivities，内部对负责人只放行「本社团发起的 + 受邀参与的联合活动」，绝不越权；
     * - 「待我确认的联合活动邀请」通过 detail(id).jointJoins 判定"我社团 join_status=0"，
     *   复用既有只读查询，不新增 Mapper 直查，也不放宽任何过滤。
     */
    private String buildLeaderFacts(String q, Long userId) {
        // 我管理的社团 ID（负责人身份，一个用户最多负责一个社团）
        Long managedClubId = managedClubIdOrNull();
        // 我所属的全部正常社团（含我负责的那个），用于展示社团基本信息
        List<Long> myClubIds = clubMemberService.myClubIds(userId);

        // 可见活动（Service 已按「本社团发起 / 受邀参与」过滤）
        List<ActivityVO> visible = activityService.listActivities(null, null);
        // 我社团发起的活动
        List<ActivityVO> initiated = visible.stream()
                .filter(a -> Objects.equals(a.getClubId(), managedClubId))
                .toList();
        // 我社团受邀参与的联合活动
        List<ActivityVO> invited = visible.stream()
                .filter(a -> a.getIsJoint() != null && a.getIsJoint() == 1
                        && !Objects.equals(a.getClubId(), managedClubId))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("【用户提问】").append(q).append("\n\n");
        sb.append("【当前身份】社团负责人（").append(roleDesc()).append("）\n");

        // ---------- 小节 1：我管理的社团 ----------
        sb.append("\n【我管理的社团】\n");
        if (myClubIds.isEmpty()) {
            sb.append("当前没有查到你有成员身份的社团记录。\n");
        } else {
            for (Long cid : myClubIds) {
                Club c = clubService.getById(cid);
                if (c == null) {
                    continue;
                }
                sb.append("- ").append(c.getName())
                        .append(Objects.equals(cid, managedClubId) ? "（你负责管理）" : "（你参与）")
                        .append("｜状态：").append(clubStatusDesc(c.getStatus()))
                        .append("｜标签：").append(c.getTags() == null || c.getTags().isBlank() ? "暂无" : c.getTags())
                        .append("\n");
            }
        }

        // ---------- 小节 2：我的活动概览（按状态分类计数 + 列名） ----------
        sb.append("\n【我的活动概览】（我社团发起 ").append(initiated.size())
                .append(" 场，受邀参与 ").append(invited.size()).append(" 场）\n");
        sb.append(formatStatusDistribution(visible));

        // ---------- 小节 3：待办事项（负责人最关心的部分） ----------
        sb.append("\n【待办事项】\n");
        boolean noTodo = true;

        // 3.1 草稿活动可提交审批
        List<ActivityVO> drafts = filterByStatus(initiated, ActivityStatus.DRAFT);
        if (drafts.isEmpty()) {
            sb.append("1. 待提交审批的草稿活动：无\n");
        } else {
            noTodo = false;
            sb.append("1. 待提交审批的草稿活动（共 ").append(drafts.size()).append(" 场）：")
                    .append(String.join("、", draftTitles(drafts)))
                    .append("。请前往「我的活动」补充信息后提交校方审批。\n");
        }

        // 3.2 待我确认的联合活动邀请（我社团作为受邀方、join_status=0）
        List<ActivityVO> pendingInvites = findPendingJointInvites(visible, managedClubId);
        if (pendingInvites.isEmpty()) {
            sb.append("2. 待你确认的联合活动邀请：无\n");
        } else {
            noTodo = false;
            sb.append("2. 待你确认的联合活动邀请（共 ").append(pendingInvites.size()).append(" 场）：")
                    .append(String.join("、", invitedDesc(pendingInvites)))
                    .append("。请前往「联合活动管理」页面确认是否参与。\n");
        }

        // 3.3 待校方审批中（等待审批结果）
        List<ActivityVO> waiting = filterByStatus(initiated, ActivityStatus.WAIT_SCHOOL_APPROVE);
        if (waiting.isEmpty()) {
            sb.append("3. 待校方审批中的活动：无\n");
        } else {
            noTodo = false;
            sb.append("3. 待校方审批中的活动（共 ").append(waiting.size()).append(" 场）：")
                    .append(String.join("、", draftTitles(waiting)))
                    .append("。已提交，等待团委老师审批结果。\n");
        }

        // 3.4 进行中且可结束的活动（经营提示，帮助负责人收尾）
        List<ActivityVO> canFinish = filterByStatus(initiated, ActivityStatus.IN_PROGRESS);
        if (!canFinish.isEmpty()) {
            noTodo = false;
            sb.append("4. 进行中的活动（共 ").append(canFinish.size()).append(" 场）：")
                    .append(String.join("、", draftTitles(canFinish)))
                    .append("。活动结束后请及时点「结束活动」以便统计归档。\n");
        }
        if (noTodo) {
            sb.append("（当前没有待处理事项，可关注后续活动报名情况。）\n");
        }

        // ---------- 小节 4：我社团的活动报名 / 签到数据 ----------
        sb.append("\n【我社团的活动报名数据】（仅统计我社团发起、已上线或已结束的活动）\n");
        List<ActivityVO> statTargets = initiated.stream()
                .filter(a -> ActivityStatus.IN_PROGRESS.getCode().equals(a.getStatus())
                        || ActivityStatus.FINISHED.getCode().equals(a.getStatus()))
                .toList();
        if (statTargets.isEmpty()) {
            sb.append("当前没有进行中或已结束的活动可供统计。\n");
        } else {
            // 控制查询规模：最多统计前 5 场，避免逐场查询报名表导致响应过慢
            for (ActivityVO a : statTargets.subList(0, Math.min(5, statTargets.size()))) {
                sb.append(formatSignupStat(a));
            }
            if (statTargets.size() > 5) {
                sb.append("（其余 ").append(statTargets.size() - 5).append(" 场活动数据请在「活动统计」页面查看）\n");
            }
        }
        // 社团整体运营统计（复用既有 Service，口径与运营分析技能一致）
        if (managedClubId != null) {
            Map<String, Object> stats = activityService.clubStats(managedClubId);
            sb.append("社团运营汇总：活动总数 ").append(stats.get("activityTotal"))
                    .append("，进行中 ").append(stats.get("inProgress"))
                    .append("，已结束 ").append(stats.get("finished"))
                    .append("，累计报名 ").append(stats.get("signupTotal"))
                    .append("，累计签到 ").append(stats.get("checkedTotal"))
                    .append("，签到率 ").append(stats.get("checkinRate")).append("。\n");
        }

        // ---------- 小节 5：我本人的报名情况（简要提及） ----------
        sb.append("\n【我本人的报名情况】\n");
        List<ActivityVO> mySigned = activityService.mySignedActivities();
        if (mySigned.isEmpty()) {
            sb.append("你本人当前没有报名任何活动。\n");
        } else {
            sb.append("你本人已报名 ").append(mySigned.size()).append(" 场活动：")
                    .append(String.join("、", mySigned.stream().map(a -> "《" + a.getTitle() + "》").toList()))
                    .append("。\n");
        }

        // ---------- 回答要求 ----------
        sb.append("\n【回答要求】\n请基于以上真实数据，用自然、专业的中文回答负责人的问题；")
                .append("数据为空时如实说明并给出下一步操作建议（如前往哪个页面处理）；")
                .append("这是「社团事务视角」，回答请围绕社团活动管理与待办事项展开；")
                .append("**不要**推荐用户去参加/加入什么社团（那是学生视角的问题，不符合负责人诉求）；")
                .append("不要编造数据中不存在的活动或统计数字。\n");
        return sb.toString();
    }

    // ==================================================================
    // 分支 C：团委老师（ADMIN）—— 审批与管理助手
    // ==================================================================

    /**
     * 管理员视角事实数据：待审批活动清单 + 全局概览 + 需关注项 + 报名签到规模
     */
    private String buildAdminFacts(String q, Long userId) {
        // 管理员可见全部活动
        List<ActivityVO> all = activityService.listActivities(null, null);
        // 待校方审批（管理员的核心待办）
        List<ActivityVO> pending = filterByStatus(all, ActivityStatus.WAIT_SCHOOL_APPROVE);
        // 社团列表（管理员可见全部状态）
        List<Club> clubs = clubService.listClubs(null);

        StringBuilder sb = new StringBuilder();
        sb.append("【用户提问】").append(q).append("\n\n");
        sb.append("【当前身份】学校管理员（团委老师），活动数据可见范围为全校。\n");

        // ---------- 小节 1：待审批活动清单（数量为 0 也要明确说明） ----------
        sb.append("\n【待审批活动】（状态：待校方审批）\n");
        if (pending.isEmpty()) {
            sb.append("当前没有待你审批的活动，审批队列已清空。\n");
        } else {
            sb.append("共有 ").append(pending.size()).append(" 场活动等待审批：\n");
            int rank = 1;
            for (ActivityVO a : pending.subList(0, Math.min(10, pending.size()))) {
                // 提交情况：报名数（待审批阶段通常为 0，但统一列出便于管理员判断）
                // 用 try/catch 保证单场统计异常不影响整体回答
                int signupCount = safeSignupCount(a.getId());
                sb.append(String.format("%d. 《%s》\n   发起社团：%s\n   时间：%s ~ %s\n   地点：%s\n   名额：%s\n   当前报名：%d 人\n",
                        rank++, a.getTitle(),
                        a.getClubName() == null ? "未知社团" : a.getClubName(),
                        a.getStartTime() == null ? "待定" : a.getStartTime(),
                        a.getEndTime() == null ? "待定" : a.getEndTime(),
                        a.getLocation() == null ? "待定" : a.getLocation(),
                        a.getCapacity() == null || a.getCapacity() == 0 ? "不限" : a.getCapacity() + "人",
                        signupCount));
            }
            if (pending.size() > 10) {
                sb.append("（其余 ").append(pending.size() - 10).append(" 场请到「活动审批」页面查看）\n");
            }
        }

        // ---------- 小节 2：全局概览 ----------
        sb.append("\n【全局概览】\n");
        sb.append("活动共 ").append(all.size()).append(" 场，状态分布：\n");
        sb.append(formatStatusDistribution(all));
        // 社团总数与状态分布
        long activeClubs = clubs.stream().filter(c -> ClubStatus.ACTIVE.getCode().equals(c.getStatus())).count();
        long pendingClubs = clubs.stream().filter(c -> ClubStatus.PENDING.getCode().equals(c.getStatus())).count();
        long rejectedClubs = clubs.stream().filter(c -> ClubStatus.REJECTED.getCode().equals(c.getStatus())).count();
        sb.append("社团共 ").append(clubs.size()).append(" 个：正常 ").append(activeClubs)
                .append(" 个，待审核 ").append(pendingClubs)
                .append(" 个，已驳回 ").append(rejectedClubs).append(" 个。\n");
        sb.append("进行中活动 ").append(filterByStatus(all, ActivityStatus.IN_PROGRESS).size()).append(" 场。\n");

        // ---------- 小节 3：需关注项 ----------
        sb.append("\n【需关注项】\n");
        // 3.1 审批积压提示
        if (pending.isEmpty()) {
            sb.append("- 审批积压：无，当前审批队列为空。\n");
        } else if (pending.size() >= 3) {
            sb.append("- 审批积压：待审批 ").append(pending.size())
                    .append(" 场，建议优先处理，避免影响社团活动筹备。\n");
        } else {
            sb.append("- 审批积压：待审批 ").append(pending.size()).append(" 场，数量正常。\n");
        }
        // 3.2 即将开始的活动（进行中且开始时间在未来）
        List<ActivityVO> upcoming = findUpcoming(all);
        if (upcoming.isEmpty()) {
            sb.append("- 即将开始的活动：无（当前没有未来开始时间的进行中活动）。\n");
        } else {
            sb.append("- 即将开始的活动（").append(upcoming.size()).append(" 场）：\n");
            for (ActivityVO a : upcoming) {
                sb.append("    《").append(a.getTitle()).append("》（")
                        .append(a.getClubName() == null ? "未知社团" : a.getClubName())
                        .append("，开始时间 ").append(a.getStartTime()).append("）\n");
            }
        }
        // 3.3 近期已结束的活动
        List<ActivityVO> recentlyFinished = findRecentlyFinished(all);
        if (recentlyFinished.isEmpty()) {
            sb.append("- 近期已结束的活动：无。\n");
        } else {
            sb.append("- 近期已结束的活动（").append(recentlyFinished.size()).append(" 场）：")
                    .append(String.join("、", draftTitles(recentlyFinished))).append("。\n");
        }

        // ---------- 小节 4：报名 / 签到总体规模（轻量统计，最多前 5 场） ----------
        sb.append("\n【报名与签到规模】（进行中/已结束活动，最多统计 5 场，避免响应过慢）\n");
        List<ActivityVO> statTargets = all.stream()
                .filter(a -> ActivityStatus.IN_PROGRESS.getCode().equals(a.getStatus())
                        || ActivityStatus.FINISHED.getCode().equals(a.getStatus()))
                .toList();
        if (statTargets.isEmpty()) {
            sb.append("当前没有进行中或已结束的活动可供统计。\n");
        } else {
            for (ActivityVO a : statTargets.subList(0, Math.min(5, statTargets.size()))) {
                sb.append(formatSignupStat(a));
            }
            if (statTargets.size() > 5) {
                sb.append("（其余 ").append(statTargets.size() - 5).append(" 场活动数据请在「数据总览」页面查看）\n");
            }
        }

        // ---------- 回答要求 ----------
        sb.append("\n【回答要求】\n请基于以上真实数据，用规范、简洁的中文回答团委老师的问题；")
                .append("数据为空时如实说明（例如「当前没有待审批活动」），不要编造数据；")
                .append("这是「审批与管理视角」，回答请围绕待审批活动、全局态势与需关注项展开；")
                .append("**不要**输出个人报名、我加入的社团等学生视角内容。\n");
        return sb.toString();
    }

    // ==================== 私有工具（通用） ====================

    /** 当前用户角色描述（用于事实清单的「当前身份」小节，便于模型用对语气） */
    private String roleDesc() {
        List<String> roles = authUtil.currentRoles();
        if (roles.contains(RoleCode.ADMIN.getCode())) {
            return RoleCode.ADMIN.getDesc();
        }
        if (roles.contains(RoleCode.CLUB_LEADER.getCode())) {
            return RoleCode.CLUB_LEADER.getDesc();
        }
        if (roles.contains(RoleCode.CLUB_PARTICIPANT.getCode())) {
            return RoleCode.CLUB_PARTICIPANT.getDesc();
        }
        return RoleCode.STUDENT.getDesc();
    }

    /** 当前用户管理的社团ID（非负责人返回 null，不抛异常，便于分支内安全取值） */
    private Long managedClubIdOrNull() {
        try {
            return authUtil.requireManagedClubId();
        } catch (BizException e) {
            return null;
        }
    }

    /** 社团状态描述 */
    private String clubStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        for (ClubStatus s : ClubStatus.values()) {
            if (s.getCode().equals(status)) {
                return s.getDesc();
            }
        }
        return "未知";
    }

    /** 按状态过滤活动 */
    private List<ActivityVO> filterByStatus(List<ActivityVO> list, ActivityStatus status) {
        return list.stream().filter(a -> status.getCode().equals(a.getStatus())).toList();
    }

    /**
     * 生成各状态分布文本（覆盖全部 6 种状态，0 也列出，管理员/负责人一眼看清分布）
     */
    private String formatStatusDistribution(List<ActivityVO> list) {
        StringBuilder sb = new StringBuilder();
        for (ActivityStatus s : ActivityStatus.values()) {
            long count = list.stream().filter(a -> s.getCode().equals(a.getStatus())).count();
            sb.append("- ").append(s.getDesc()).append("：").append(count).append(" 场\n");
        }
        return sb.toString();
    }

    /** 活动标题列表：统一《》包裹，便于模型直接引用 */
    private List<String> draftTitles(List<ActivityVO> list) {
        return list.stream().map(a -> "《" + a.getTitle() + "》").toList();
    }

    /** 联合活动邀请描述：带发起社团，方便负责人判断是否参与 */
    private List<String> invitedDesc(List<ActivityVO> list) {
        return list.stream()
                .map(a -> "《" + a.getTitle() + "》（发起社团：" + (a.getClubName() == null ? "未知" : a.getClubName()) + "）")
                .toList();
    }

    /**
     * 找出「待我确认的联合活动邀请」：
     * 遍历可见的联合活动（状态=待社团确认、发起方不是我社团），
     * 再通过 detail(id).jointJoins 判定我社团的 join_status 是否为 0（待确认）。
     * 说明：join_status=0 与"已拒绝(2)"都可能是状态=待社团确认，故必须显式校验 join_status，
     * 不能只看活动状态，否则会把已拒绝的邀请误报为待办。
     */
    private List<ActivityVO> findPendingJointInvites(List<ActivityVO> visible, Long managedClubId) {
        List<ActivityVO> result = new ArrayList<>();
        if (managedClubId == null) {
            return result;
        }
        for (ActivityVO a : visible) {
            if (a.getIsJoint() == null || a.getIsJoint() != 1) {
                continue;
            }
            // 我是发起方 -> 不是"待我确认"，跳过
            if (Objects.equals(a.getClubId(), managedClubId)) {
                continue;
            }
            if (!ActivityStatus.WAIT_CLUB_CONFIRM.getCode().equals(a.getStatus())) {
                continue;
            }
            ActivityVO detail = activityService.detail(a.getId());
            boolean waitMe = detail.getJointJoins() != null && detail.getJointJoins().stream()
                    .anyMatch(j -> Objects.equals(j.getClubId(), managedClubId)
                            && JointJoinStatus.WAIT_CONFIRM.getCode().equals(j.getJoinStatus()));
            if (waitMe) {
                result.add(a);
            }
        }
        return result;
    }

    /**
     * 单场活动的报名/签到统计文本（数据权限：listSignups 内部已按角色过滤，
     * 发起负责人/管理员可看全量，受邀负责人只看本社团成员）
     */
    private String formatSignupStat(ActivityVO a) {
        List<SignupVO> signups = safeSignups(a.getId());
        long checked = signups.stream().filter(s -> Integer.valueOf(1).equals(s.getChecked())).count();
        String rate = signups.isEmpty() ? "-" : Math.round(checked * 100.0 / signups.size()) + "%";
        return "- 《" + a.getTitle() + "》（" + (a.getStatusDesc() == null ? "未知状态" : a.getStatusDesc())
                + "）：报名 " + signups.size() + " 人，签到 " + checked + " 人，签到率 " + rate + "\n";
    }

    /** 安全获取报名列表（异常时返回空列表，保证单场统计失败不影响整体回答） */
    private List<SignupVO> safeSignups(Long activityId) {
        try {
            return activityService.listSignups(activityId);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 安全获取报名人数 */
    private int safeSignupCount(Long activityId) {
        return safeSignups(activityId).size();
    }

    /** 即将开始的活动：进行中且开始时间在当前时间之后（按开始时间升序） */
    private List<ActivityVO> findUpcoming(List<ActivityVO> all) {
        LocalDateTime now = LocalDateTime.now();
        return all.stream()
                .filter(a -> ActivityStatus.IN_PROGRESS.getCode().equals(a.getStatus()))
                .filter(a -> {
                    LocalDateTime start = parseTime(a.getStartTime());
                    return start != null && start.isAfter(now);
                })
                .sorted((x, y) -> {
                    LocalDateTime sx = parseTime(x.getStartTime());
                    LocalDateTime sy = parseTime(y.getStartTime());
                    if (sx == null || sy == null) {
                        return 0;
                    }
                    return sx.compareTo(sy);
                })
                .limit(5)
                .toList();
    }

    /** 近期已结束的活动：已结束状态按开始时间倒序取前 3 场 */
    private List<ActivityVO> findRecentlyFinished(List<ActivityVO> all) {
        return all.stream()
                .filter(a -> ActivityStatus.FINISHED.getCode().equals(a.getStatus()))
                .sorted((x, y) -> {
                    LocalDateTime sx = parseTime(x.getStartTime());
                    LocalDateTime sy = parseTime(y.getStartTime());
                    if (sx == null || sy == null) {
                        return 0;
                    }
                    return sy.compareTo(sx);
                })
                .limit(3)
                .toList();
    }

    // ==================== 私有工具（学生分支复用，逻辑保持原样） ====================

    /**
     * 按提问关键词做基础筛选：
     * - 命中「新手/入门/零基础/第一次」-> 优先低名额、非大型的轻量活动
     * - 命中地点关键词 -> 按活动地点匹配
     * - 命中时间关键词（今天/明天/本周/周末）-> 按时间匹配
     * - 无特征命中 -> 返回全部进行中活动（交给大模型综合排序说明）
     */
    private List<ActivityVO> filterByQuestion(List<ActivityVO> ongoing, String q) {
        List<ActivityVO> result = new ArrayList<>();
        boolean beginner = q.contains("新手") || q.contains("入门") || q.contains("零基础")
                || q.contains("第一次") || q.contains("小白") || q.contains("简单");
        boolean today = q.contains("今天");
        boolean tomorrow = q.contains("明天");
        boolean weekend = q.contains("周末") || q.contains("周六") || q.contains("周日");

        LocalDateTime now = LocalDateTime.now();
        for (ActivityVO a : ongoing) {
            LocalDateTime start = parseTime(a.getStartTime());
            // 新手友好：名额不大于 80 或介绍中含「新手/初级/零基础/体验」
            if (beginner) {
                int cap = a.getCapacity() == null ? 0 : a.getCapacity();
                String intro = a.getIntro() == null ? "" : a.getIntro();
                boolean light = (cap > 0 && cap <= 80)
                        || intro.contains("新手") || intro.contains("初级") || intro.contains("零基础")
                        || intro.contains("体验") || intro.contains("入门");
                if (!light) {
                    continue;
                }
            }
            // 时间筛选
            if ((today || tomorrow || weekend) && start != null) {
                if (today && !start.toLocalDate().equals(now.toLocalDate())) {
                    continue;
                }
                if (tomorrow && !start.toLocalDate().equals(now.toLocalDate().plusDays(1))) {
                    continue;
                }
                if (weekend) {
                    int dow = start.getDayOfWeek().getValue();
                    if (dow != 6 && dow != 7) {
                        continue;
                    }
                }
            }
            // 地点筛选：从提问中提取「在/去 XXX」形式的地点词，命中活动地点
            String location = a.getLocation();
            if (location != null && !location.isBlank()) {
                String loc = location.replace("楼", "").replace("室", "").replace("场", "");
                if (loc.length() >= 2 && q.contains(loc)) {
                    result.add(a);
                    continue;
                }
            }
            result.add(a);
        }
        return result;
    }

    /** 格式化单条活动信息（供模型组织语言），含实时剩余名额 */
    private String formatActivity(int rank, ActivityVO a) {
        // 剩余名额 = 名额 - 报名总人数。报名总人数用聚合计数 countSignups（不按角色过滤，
        // 学生视角也拿真实总数，避免学生分支因 listSignups 的角色过滤导致"已报 0/30"失真）；
        // 签到明细统计仍走 listSignups（按角色过滤）。名额 0/空 视为不限。
        // 直接给出数字，模型无需自行推算，避免算错。
        int capacity = a.getCapacity() == null ? 0 : a.getCapacity();
        int signedCount;
        try {
            signedCount = activityService.countSignups(a.getId());
        } catch (Exception e) {
            signedCount = 0;
        }
        String remain;
        if (capacity == 0) {
            remain = "不限（已报 " + signedCount + " 人）";
        } else {
            int left = Math.max(0, capacity - signedCount);
            remain = left <= 0 ? "已满" : "剩余 " + left + " 人（已报 " + signedCount + "/" + capacity + "）";
        }
        return String.format("%d. 《%s》\n   发起社团：%s\n   时间：%s ~ %s\n   地点：%s\n   名额：%s\n   剩余名额：%s\n",
                rank, a.getTitle(),
                a.getClubName() == null ? "未知社团" : a.getClubName(),
                a.getStartTime() == null ? "待定" : a.getStartTime(),
                a.getEndTime() == null ? "待定" : a.getEndTime(),
                a.getLocation() == null ? "待定" : a.getLocation(),
                capacity == 0 ? "不限" : capacity + "人",
                remain);
    }

    /** 解析 Service 返回的字符串时间，失败返回 null */
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
