package com.club.agent.tools;

import com.club.dto.ActivityVO;
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

/**
 * 个性化活动推荐技能（学生首页自动加载）
 * 权限：登录用户本人，只推荐「进行中且未报名」的活动，并按兴趣标签匹配排序
 * 注：数据全部来自已有 Service，Tool 内不直查 Mapper
 */
@Component
public class ActivityRecommendTool {

    private final AuthUtil authUtil;
    private final ActivityService activityService;
    private final ClubMemberService clubMemberService;
    private final ClubService clubService;

    public ActivityRecommendTool(AuthUtil authUtil, ActivityService activityService,
                                 ClubMemberService clubMemberService, ClubService clubService) {
        this.authUtil = authUtil;
        this.activityService = activityService;
        this.clubMemberService = clubMemberService;
        this.clubService = clubService;
    }

    @Tool(description = "为当前登录学生生成个性化活动推荐列表：过滤已报名活动，按用户所在社团的兴趣标签匹配排序，并输出推荐理由")
    public String recommendActivities(
            @ToolParam(description = "用户当前想探索的新方向，可为空字符串", required = false) String extraInput) {
        // 权限校验：必须登录（Sa-Token 拦截器已保证），推荐只看本人视角数据
        Long userId = authUtil.currentUserId();
        // 复用已有 Service：进行中活动（学生视角自动只看可报名活动）
        List<ActivityVO> ongoing = activityService.listActivities(3, null);
        List<Long> myClubIds = clubMemberService.myClubIds(userId);
        List<String> myTags = myClubIds.stream()
                .map(clubService::getById)
                .filter(c -> c != null)
                .map(Club::getTags)
                .filter(t -> t != null && !t.isBlank())
                .flatMap(t -> List.of(t.split(",")).stream())
                .map(String::trim)
                .toList();

        // 过滤已报名的，按标签匹配度排序（标签命中多的排前面）
        List<ActivityVO> candidates = ongoing.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getSigned()))
                .sorted((a, b) -> score(b, myTags) - score(a, myTags))
                .toList();

        if (candidates.isEmpty()) {
            return "当前暂无可推荐的新活动：您已报名全部进行中的活动，或暂无进行中的活动。可以浏览社团列表，等待新活动上线。";
        }

        StringBuilder sb = new StringBuilder("【个性化活动推荐】（根据您加入社团的兴趣标签匹配）\n\n");
        int rank = 1;
        for (ActivityVO a : candidates.subList(0, Math.min(5, candidates.size()))) {
            sb.append(String.format("%d. 《%s》\n   发起社团：%s | 地点：%s | 时间：%s 至 %s\n   推荐理由：%s\n\n",
                    rank++, a.getTitle(), a.getClubName(), a.getLocation(), a.getStartTime(), a.getEndTime(),
                    reason(a, myTags)));
        }
        if (extraInput != null && !extraInput.isBlank()) {
            sb.append("（已结合您的偏好：").append(extraInput).append("）");
        }
        return sb.toString();
    }

    /** 标签匹配打分：活动发起社团的标签与用户兴趣标签的交集数 */
    private int score(ActivityVO a, List<String> myTags) {
        Club club = a.getClubId() == null ? null : clubService.getById(a.getClubId());
        if (club == null || club.getTags() == null) {
            return 0;
        }
        int hit = 0;
        for (String tag : club.getTags().split(",")) {
            if (myTags.contains(tag.trim())) {
                hit++;
            }
        }
        return hit;
    }

    /** 推荐理由文案 */
    private String reason(ActivityVO a, List<String> myTags) {
        Club club = a.getClubId() == null ? null : clubService.getById(a.getClubId());
        boolean tagHit = club != null && club.getTags() != null
                && List.of(club.getTags().split(",")).stream().map(String::trim).anyMatch(myTags::contains);
        if (tagHit) {
            return "与您所在社团的兴趣标签高度匹配，报名通道已开放。";
        }
        return "热门进行中活动，报名通道已开放，值得一试。";
    }
}
