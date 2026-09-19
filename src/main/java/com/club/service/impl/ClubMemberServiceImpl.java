package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.entity.Club;
import com.club.entity.ClubMember;
import com.club.entity.SysUser;
import com.club.mapper.ClubMapper;
import com.club.mapper.ClubMemberMapper;
import com.club.mapper.SysUserMapper;
import com.club.service.ClubMemberService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社团成员服务实现
 */
@Service
public class ClubMemberServiceImpl extends ServiceImpl<ClubMemberMapper, ClubMember> implements ClubMemberService {

    /** 成员状态常量（表内小状态，用常量即可） */
    public static final int MEMBER_PENDING = 0;
    public static final int MEMBER_NORMAL = 1;
    public static final int MEMBER_QUIT = 2;

    private final SysUserMapper userMapper;
    private final ClubMapper clubMapper;
    private final AuthUtil authUtil;

    public ClubMemberServiceImpl(SysUserMapper userMapper, ClubMapper clubMapper, AuthUtil authUtil) {
        this.userMapper = userMapper;
        this.clubMapper = clubMapper;
        this.authUtil = authUtil;
    }

    @Override
    public void join(Long clubId) {
        Long userId = authUtil.currentUserId();
        Club club = clubMapper.selectById(clubId);
        if (club == null || club.getStatus() == null || club.getStatus() != 1) {
            throw new BizException("社团不存在或未开放加入");
        }
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .ne(ClubMember::getStatus, MEMBER_QUIT));
        if (exists > 0) {
            throw new BizException("您已申请或已是该社团成员");
        }
        ClubMember member = new ClubMember();
        member.setClubId(clubId);
        member.setUserId(userId);
        member.setMemberRole("会员");
        member.setStatus(MEMBER_PENDING);
        member.setCreateTime(LocalDateTime.now());
        baseMapper.insert(member);
    }

    @Override
    public void quit(Long clubId) {
        Long userId = authUtil.currentUserId();
        ClubMember member = baseMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .ne(ClubMember::getStatus, MEMBER_QUIT));
        if (member == null) {
            throw new BizException("您不是该社团成员");
        }
        // 负责人不能直接退社（需先换届，本版从简：禁止）
        if ("会长".equals(member.getMemberRole())) {
            throw new BizException("社团负责人不能直接退出社团");
        }
        member.setStatus(MEMBER_QUIT);
        baseMapper.updateById(member);
    }

    @Override
    public void auditMember(Long memberId, boolean approved) {
        ClubMember member = baseMapper.selectById(memberId);
        if (member == null) {
            throw new BizException("成员记录不存在");
        }
        // 功能权限 + 数据权限：仅本社团负责人可审核
        authUtil.checkClubLeader(member.getClubId());
        if (member.getStatus() == null || member.getStatus() != MEMBER_PENDING) {
            throw new BizException("该申请不在待审核状态");
        }
        member.setStatus(approved ? MEMBER_NORMAL : MEMBER_QUIT);
        if (approved) {
            member.setJoinTime(LocalDateTime.now());
        }
        baseMapper.updateById(member);
    }

    @Override
    public void addLeaderMember(Long clubId, Long userId, String memberRole) {
        ClubMember member = new ClubMember();
        member.setClubId(clubId);
        member.setUserId(userId);
        member.setMemberRole(memberRole);
        member.setStatus(MEMBER_NORMAL);
        member.setJoinTime(LocalDateTime.now());
        member.setCreateTime(LocalDateTime.now());
        baseMapper.insert(member);
    }

    @Override
    public List<Map<String, Object>> listMembers(Long clubId) {
        // 功能权限 + 数据权限：仅本社团负责人（或管理员）可查成员列表
        authUtil.checkClubLeader(clubId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClubMember m : baseMapper.selectList(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, clubId)
                .ne(ClubMember::getStatus, MEMBER_QUIT)
                .orderByAsc(ClubMember::getStatus))) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", m.getId());
            row.put("userId", m.getUserId());
            row.put("memberRole", m.getMemberRole());
            row.put("status", m.getStatus());
            row.put("statusDesc", m.getStatus() == MEMBER_NORMAL ? "正常" : "待审核");
            SysUser user = userMapper.selectById(m.getUserId());
            if (user != null) {
                row.put("userName", user.getName());
                row.put("studentNo", user.getStudentNo());
            }
            result.add(row);
        }
        return result;
    }

    @Override
    public ClubMember getLeaderMember(Long clubId, Long userId) {
        // 负责人判定：member_role = '会长'
        return baseMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, MEMBER_NORMAL)
                .eq(ClubMember::getMemberRole, "会长"));
    }

    @Override
    public Long getManagedClubId(Long userId) {
        ClubMember leader = baseMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, MEMBER_NORMAL)
                .eq(ClubMember::getMemberRole, "会长")
                .last("LIMIT 1"));
        return leader == null ? null : leader.getClubId();
    }

    @Override
    public boolean isNormalMember(Long clubId, Long userId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, MEMBER_NORMAL)) > 0;
    }

    @Override
    public List<Long> myClubIds(Long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MEMBER_NORMAL)).stream()
                .map(ClubMember::getClubId).toList();
    }
}
