package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.dto.ClubDTO;
import com.club.entity.Club;
import com.club.entity.SysRole;
import com.club.entity.SysUserRole;
import com.club.enums.ClubStatus;
import com.club.mapper.ClubMapper;
import com.club.mapper.SysRoleMapper;
import com.club.mapper.SysUserRoleMapper;
import com.club.service.ClubService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 社团服务实现
 */
@Service
public class ClubServiceImpl extends ServiceImpl<ClubMapper, Club> implements ClubService {

    private final AuthUtil authUtil;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final com.club.service.ClubMemberService clubMemberService;

    public ClubServiceImpl(AuthUtil authUtil, SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                           com.club.service.ClubMemberService clubMemberService) {
        this.authUtil = authUtil;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.clubMemberService = clubMemberService;
    }

    @Override
    public List<Club> listClubs(String keyword) {
        LambdaQueryWrapper<Club> qw = new LambdaQueryWrapper<>();
        // 学生浏览只看正常社团，管理员可看全部
        if (!authUtil.isAdmin()) {
            qw.eq(Club::getStatus, ClubStatus.ACTIVE.getCode());
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Club::getName, keyword);
        }
        qw.orderByDesc(Club::getCreateTime);
        return list(qw);
    }

    @Override
    public void apply(ClubDTO dto) {
        Long userId = authUtil.currentUserId();
        if (authUtil.isLeader()) {
            throw new BizException("您已有负责人身份，无需重复申请创建社团");
        }
        Club club = new Club();
        club.setName(dto.getName());
        club.setIntro(dto.getIntro());
        club.setTags(dto.getTags());
        // 申请人默认成为该社团负责人
        club.setLeaderId(userId);
        club.setStatus(ClubStatus.PENDING.getCode());
        save(club);
        // 同步写入成员表（会长）
        clubMemberService.addLeaderMember(club.getId(), userId, "会长");
    }

    @Override
    public List<Club> listPending() {
        authUtil.checkAdmin();
        return list(new LambdaQueryWrapper<Club>().eq(Club::getStatus, ClubStatus.PENDING.getCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(ClubDTO dto) {
        authUtil.checkAdmin();
        Club club = getById(dto.getId());
        if (club == null) {
            throw new BizException("社团不存在");
        }
        if (!ClubStatus.PENDING.getCode().equals(club.getStatus())) {
            throw new BizException("该社团不在待审核状态");
        }
        if (Boolean.TRUE.equals(dto.getAuditResult()) || dto.getAuditResult() != null && dto.getAuditResult() == 1) {
            club.setStatus(ClubStatus.ACTIVE.getCode());
            updateById(club);
            // 审核通过后授予申请人「社团发起负责人」角色
            grantLeaderRole(club.getLeaderId());
        } else {
            if (dto.getRejectReason() == null || dto.getRejectReason().isBlank()) {
                throw new BizException("驳回必须填写理由");
            }
            club.setStatus(ClubStatus.REJECTED.getCode());
            club.setRejectReason(dto.getRejectReason());
            updateById(club);
        }
    }

    /** 给用户授予负责人角色（若已有则跳过） */
    private void grantLeaderRole(Long userId) {
        SysRole leaderRole = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, "CLUB_LEADER")).stream().findFirst().orElse(null);
        if (leaderRole == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, leaderRole.getId()));
        if (count == 0) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(leaderRole.getId());
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Club> myManagedClubs() {
        Long userId = authUtil.currentUserId();
        return list(new LambdaQueryWrapper<Club>().eq(Club::getLeaderId, userId));
    }

    @Override
    public Club detail(Long id) {
        Club club = getById(id);
        if (club == null) {
            throw new BizException("社团不存在");
        }
        return club;
    }
}
