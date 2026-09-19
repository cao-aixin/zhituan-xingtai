package com.club.util;

import cn.dev33.satoken.stp.StpUtil;
import com.club.entity.ClubMember;
import com.club.enums.RoleCode;
import com.club.mapper.ClubMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 权限工具类：统一封装「功能权限 + 数据权限」判断
 * 直接使用 Mapper 查询成员记录，避免与 Service 层形成循环依赖
 */
@Component
public class AuthUtil {

    private final ClubMemberMapper clubMemberMapper;

    public AuthUtil(ClubMemberMapper clubMemberMapper) {
        this.clubMemberMapper = clubMemberMapper;
    }

    /** 当前登录用户ID（未登录时 Sa-Token 拦截器已拦截，这里直接取） */
    public Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 当前用户角色编码列表 */
    public List<String> currentRoles() {
        return StpUtil.getRoleList();
    }

    /** 是否学校管理员 */
    public boolean isAdmin() {
        return currentRoles().contains(RoleCode.ADMIN.getCode());
    }

    /** 是否社团负责人层级（发起负责人 / 参与负责人） */
    public boolean isLeader() {
        return currentRoles().stream().anyMatch(RoleCode::isLeaderRole);
    }

    /** 断言：是管理员，否则拒绝 */
    public void checkAdmin() {
        if (!isAdmin()) {
            throw new BizException(403, "无权限：该操作仅学校管理员可执行");
        }
    }

    /** 成员状态：1正常（与 ClubMemberServiceImpl.MEMBER_NORMAL 一致） */
    private static final int MEMBER_NORMAL = 1;

    /**
     * 断言：当前用户是指定社团的负责人（功能权限 + 数据权限）
     * 负责人判定：club_member.member_role = '会长'
     */
    public void checkClubLeader(Long clubId) {
        Long userId = currentUserId();
        ClubMember member = clubMemberMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, MEMBER_NORMAL)
                .eq(ClubMember::getMemberRole, "会长"));
        if (member == null && !isAdmin()) {
            throw new BizException(403, "无权限：您不是该社团的负责人");
        }
    }

    /**
     * 断言：当前用户是某个社团的负责人，并返回该社团ID（用于数据过滤）
     */
    public Long requireManagedClubId() {
        ClubMember leader = clubMemberMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getUserId, currentUserId())
                .eq(ClubMember::getStatus, MEMBER_NORMAL)
                .eq(ClubMember::getMemberRole, "会长")
                .last("LIMIT 1"));
        if (leader == null) {
            throw new BizException(403, "无权限：您没有管理的社团");
        }
        return leader.getClubId();
    }
}
