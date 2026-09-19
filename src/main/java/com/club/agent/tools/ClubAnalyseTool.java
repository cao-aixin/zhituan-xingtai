package com.club.agent.tools;

import com.club.entity.Club;
import com.club.service.ActivityService;
import com.club.service.ClubMemberService;
import com.club.service.ClubService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 社团运营分析技能（统计页「AI分析」按钮）
 * 权限：仅该社团负责人 / 学校管理员可调用，防止越权查别的社团数据
 * 注：查询全部复用已有 Service，不在 Tool 内直查 Mapper
 */
@Component
public class ClubAnalyseTool {

    private final AuthUtil authUtil;
    private final ClubService clubService;
    private final ClubMemberService clubMemberService;
    private final ActivityService activityService;

    public ClubAnalyseTool(AuthUtil authUtil, ClubService clubService,
                           ClubMemberService clubMemberService, ActivityService activityService) {
        this.authUtil = authUtil;
        this.clubService = clubService;
        this.clubMemberService = clubMemberService;
        this.activityService = activityService;
    }

    @Tool(description = "分析指定社团的运营情况：基于成员规模、活动数量、报名与签到数据，输出运营现状分析与改进建议")
    public String analyseClub(
            @ToolParam(description = "社团ID") Long clubId,
            @ToolParam(description = "用户关注的分析重点，可为空字符串", required = false) String extraInput) {
        // 权限校验：非本社团负责人且非管理员 -> 拒绝（防越权）
        authUtil.checkClubLeader(clubId);
        Club club = clubService.getById(clubId);
        if (club == null) {
            throw new BizException("社团不存在");
        }
        // 复用 Service 拿真实数据
        Map<String, Object> stats = activityService.clubStats(clubId);
        List<Map<String, Object>> members = clubMemberService.listMembers(clubId);
        long normalMembers = members.stream().filter(m -> Integer.valueOf(1).equals(m.get("status"))).count();
        long pendingMembers = members.size() - normalMembers;

        return String.format("""
                【社团运营分析】%s（标签：%s）

                【规模指标】
                - 正式成员：%d 人（另有 %d 份入社申请待审核）
                - 累计活动：%s 场（其中联合活动 %s 场）
                - 进行中活动：%s 场，已结束：%s 场

                【活跃指标】
                - 累计报名：%s 人次
                - 累计签到：%s 人次，整体到场率：%s

                【分析结论】
                %s

                【改进建议】
                1. %s
                2. 建议定期清理待审核申请，保持成员数据准确；
                3. 结合成员标签偏好策划下一期活动，提升报名转化。%s""",
                club.getName(),
                club.getTags() == null ? "未设置" : club.getTags(),
                normalMembers, pendingMembers,
                stats.get("activityTotal"), stats.get("jointActivityTotal"),
                stats.get("inProgress"), stats.get("finished"),
                stats.get("signupTotal"), stats.get("checkedTotal"), stats.get("checkinRate"),
                normalMembers >= 10 ? "社团规模较健康，活动供给稳定，可尝试跨社团联动扩大影响力。"
                        : "社团规模偏小，建议先以招新和轻量活动积累核心成员。",
                pendingMembers > 0 ? "及时处理 " + pendingMembers + " 份待审核入社申请，提升新成员归属感。" : "成员审核及时，保持现状。",
                extraInput == null || extraInput.isBlank() ? "" : "\n\n【重点关注】" + extraInput);
    }
}
