package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.dto.HandoverDTO;
import com.club.dto.HandoverVO;
import com.club.entity.Club;
import com.club.entity.ClubHandover;
import com.club.entity.ClubMember;
import com.club.entity.SysRole;
import com.club.entity.SysUser;
import com.club.entity.SysUserRole;
import com.club.enums.ClubStatus;
import com.club.enums.MessageType;
import com.club.enums.RoleCode;
import com.club.mapper.ClubHandoverMapper;
import com.club.mapper.ClubMapper;
import com.club.mapper.ClubMemberMapper;
import com.club.mapper.SysRoleMapper;
import com.club.mapper.SysUserMapper;
import com.club.mapper.SysUserRoleMapper;
import com.club.service.ClubHandoverService;
import com.club.service.MessageService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社团换届服务实现
 * 流程：校验负责人权限 -> 变更 club.leader_id -> 维护成员表会长 ->
 *       写换届记录 -> 站内消息通知新旧负责人
 */
@Service
public class ClubHandoverServiceImpl extends ServiceImpl<ClubHandoverMapper, ClubHandover> implements ClubHandoverService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClubMapper clubMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final MessageService messageService;
    private final AuthUtil authUtil;

    public ClubHandoverServiceImpl(ClubMapper clubMapper, ClubMemberMapper clubMemberMapper,
                                   SysUserMapper userMapper, SysRoleMapper roleMapper,
                                   SysUserRoleMapper userRoleMapper, MessageService messageService,
                                   AuthUtil authUtil) {
        this.clubMapper = clubMapper;
        this.clubMemberMapper = clubMemberMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.messageService = messageService;
        this.authUtil = authUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handover(HandoverDTO dto) {
        Club club = clubMapper.selectById(dto.getClubId());
        if (club == null) {
            throw new BizException("社团不存在");
        }
        if (!ClubStatus.ACTIVE.getCode().equals(club.getStatus())) {
            throw new BizException("社团状态异常，无法发起换届");
        }
        // 功能+数据权限：仅本社团负责人（或管理员）可发起换届
        authUtil.checkClubLeader(dto.getClubId());

        // 按学号定位新负责人
        SysUser newLeader = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentNo, dto.getNewLeaderStudentNo()));
        if (newLeader == null) {
            throw new BizException("新负责人账号不存在");
        }
        if (newLeader.getId().equals(club.getLeaderId())) {
            throw new BizException("该用户已是当前社团负责人");
        }
        Long oldLeaderId = club.getLeaderId();

        // 1. 变更社团负责人
        club.setLeaderId(newLeader.getId());
        clubMapper.updateById(club);

        // 2. 新负责人写入成员表为会长（已存在记录则提升为会长并置为正常）
        ClubMember exist = clubMemberMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, dto.getClubId())
                .eq(ClubMember::getUserId, newLeader.getId()));
        if (exist == null) {
            ClubMember member = new ClubMember();
            member.setClubId(dto.getClubId());
            member.setUserId(newLeader.getId());
            member.setMemberRole("会长");
            member.setStatus(1);
            member.setJoinTime(LocalDateTime.now());
            member.setCreateTime(LocalDateTime.now());
            clubMemberMapper.insert(member);
        } else {
            exist.setMemberRole("会长");
            exist.setStatus(1);
            clubMemberMapper.updateById(exist);
        }

        // 3. 新负责人若尚无负责人层级角色，授予 CLUB_LEADER（保证换届后可用负责人功能）
        List<String> roleCodes = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, newLeader.getId()))
                .stream()
                .map(ur -> roleMapper.selectById(ur.getRoleId()))
                .filter(Objects::nonNull)
                .map(SysRole::getRoleCode)
                .toList();
        if (roleCodes.stream().noneMatch(RoleCode::isLeaderRole)) {
            SysRole leaderRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, RoleCode.CLUB_LEADER.getCode()));
            if (leaderRole != null) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(newLeader.getId());
                ur.setRoleId(leaderRole.getId());
                userRoleMapper.insert(ur);
            }
        }

        // 4. 写换届记录
        ClubHandover handover = new ClubHandover();
        handover.setClubId(dto.getClubId());
        handover.setOldLeaderId(oldLeaderId);
        handover.setNewLeaderId(newLeader.getId());
        handover.setNote(dto.getNote() == null ? "" : dto.getNote());
        handover.setCreateTime(LocalDateTime.now());
        save(handover);

        // 5. 站内消息通知新旧负责人
        messageService.send(List.of(newLeader.getId()), "换届交接通知",
                "您已成为《" + club.getName() + "》新任负责人，历史资料已完整移交。",
                MessageType.NOTICE.getCode(), club.getId());
        messageService.send(List.of(oldLeaderId), "换届完成通知",
                "《" + club.getName() + "》负责人已移交给" + newLeader.getName() + "。",
                MessageType.NOTICE.getCode(), club.getId());
    }

    @Override
    public List<HandoverVO> list(Long clubId) {
        // 数据权限：管理员全部；社团负责人仅本社团；学生无权查看
        Long visibleClubId = resolveVisibleClubId(clubId);
        List<ClubHandover> records = list(new LambdaQueryWrapper<ClubHandover>()
                .eq(visibleClubId != null, ClubHandover::getClubId, visibleClubId)
                .orderByDesc(ClubHandover::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        // 批量填充展示字段：社团名 / 新旧负责人姓名
        Map<Long, String> clubNames = clubMapper.selectList(new LambdaQueryWrapper<Club>()
                        .in(Club::getId, records.stream().map(ClubHandover::getClubId).distinct().toList()))
                .stream().collect(Collectors.toMap(Club::getId, Club::getName));
        Map<Long, String> userNames = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, records.stream()
                                .flatMap(h -> java.util.stream.Stream.of(h.getOldLeaderId(), h.getNewLeaderId()))
                                .distinct().toList()))
                .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getName));
        return records.stream().map(h -> {
            HandoverVO vo = new HandoverVO();
            vo.setId(h.getId());
            vo.setClubId(h.getClubId());
            vo.setClubName(clubNames.get(h.getClubId()));
            vo.setOldLeaderId(h.getOldLeaderId());
            vo.setOldLeaderName(userNames.get(h.getOldLeaderId()));
            vo.setNewLeaderId(h.getNewLeaderId());
            vo.setNewLeaderName(userNames.get(h.getNewLeaderId()));
            vo.setNote(h.getNote());
            vo.setCreateTime(h.getCreateTime() == null ? null : h.getCreateTime().format(FMT));
            return vo;
        }).toList();
    }

    /**
     * 数据权限解析：返回实际可查询的社团ID（null 表示可查全部）
     */
    private Long resolveVisibleClubId(Long clubId) {
        if (authUtil.isAdmin()) {
            return clubId;
        }
        if (authUtil.isLeader()) {
            Long myClubId = authUtil.requireManagedClubId();
            if (clubId != null && !Objects.equals(clubId, myClubId)) {
                throw new BizException(403, "无权限：只能查看本社团的换届记录");
            }
            return myClubId;
        }
        throw new BizException(403, "无权限：仅学校管理员与社团负责人可查看换届记录");
    }
}
