package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.dto.ActivityAuditDTO;
import com.club.dto.ActivityDTO;
import com.club.dto.ActivityVO;
import com.club.dto.JointConfirmDTO;
import com.club.dto.SignupDTO;
import com.club.dto.SignupVO;
import com.club.entity.Activity;
import com.club.entity.Club;
import com.club.entity.ClubMember;
import com.club.entity.JointActivityJoin;
import com.club.entity.SysUser;
import com.club.enums.ActivityStatus;
import com.club.enums.JointJoinStatus;
import com.club.enums.MessageType;
import com.club.mapper.ActivityMapper;
import com.club.mapper.ActivitySignupMapper;
import com.club.entity.ActivitySignup;
import com.club.mapper.ClubMapper;
import com.club.mapper.ClubMemberMapper;
import com.club.mapper.JointActivityJoinMapper;
import com.club.mapper.SysUserMapper;
import com.club.service.ActivityService;
import com.club.service.MessageService;
import com.club.service.UserService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动服务实现：普通活动 + 联合活动完整状态机
 * 状态流转：0草稿 -> 2待校方审批 -> 3进行中 -> 4已结束（普通活动）
 *          1待社团确认 -> 2待校方审批 -> 3进行中 -> 4已结束（联合活动）
 *          审批驳回 -> 5已驳回
 */
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements ActivityService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JointActivityJoinMapper joinMapper;
    private final ActivitySignupMapper signupMapper;
    private final ClubMapper clubMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final SysUserMapper userMapper;
    private final MessageService messageService;
    private final UserService userService;
    private final AuthUtil authUtil;

    public ActivityServiceImpl(JointActivityJoinMapper joinMapper, ActivitySignupMapper signupMapper,
                               ClubMapper clubMapper, ClubMemberMapper clubMemberMapper,
                               SysUserMapper userMapper, MessageService messageService,
                               UserService userService, AuthUtil authUtil) {
        this.joinMapper = joinMapper;
        this.signupMapper = signupMapper;
        this.clubMapper = clubMapper;
        this.clubMemberMapper = clubMemberMapper;
        this.userMapper = userMapper;
        this.messageService = messageService;
        this.userService = userService;
        this.authUtil = authUtil;
    }

    // ==================== 创建 / 编辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ActivityDTO dto) {
        // 功能权限：只有社团负责人（或管理员）能创建活动
        Long clubId = resolveInitiatorClubId();
        Activity activity = new Activity();
        copyDto(dto, activity);
        activity.setClubId(clubId);
        activity.setCreatorId(authUtil.currentUserId());
        activity.setUpdateTime(LocalDateTime.now());

        boolean joint = dto.getIsJoint() != null && dto.getIsJoint() == 1;
        if (joint) {
            createJoint(activity, dto.getInviteClubIds(), dto);
        } else {
            // 普通活动：先入草稿，确认后可提交审批
            activity.setIsJoint(0);
            activity.setStatus(ActivityStatus.DRAFT.getCode());
            save(activity);
        }
        return activity.getId();
    }

    /** 创建联合活动：写主表 + 受邀社团记录，状态=待社团确认，推送待办消息 */
    private void createJoint(Activity activity, List<Long> inviteClubIds, ActivityDTO dto) {
        if (inviteClubIds == null || inviteClubIds.isEmpty()) {
            throw new BizException("联合活动必须选择至少一个受邀社团");
        }
        if (inviteClubIds.contains(activity.getClubId())) {
            throw new BizException("受邀社团不能包含发起社团自身");
        }
        if (dto.getSafetyOfficer() == null || dto.getSafetyOfficer().isBlank()) {
            throw new BizException("联合活动必须填写安全负责人");
        }
        activity.setIsJoint(1);
        activity.setStatus(ActivityStatus.WAIT_CLUB_CONFIRM.getCode());
        save(activity);

        List<Long> notifiedLeaderIds = new ArrayList<>();
        for (Long clubId : inviteClubIds) {
            Club invited = clubMapper.selectById(clubId);
            if (invited == null || invited.getStatus() == null || invited.getStatus() != 1) {
                throw new BizException("受邀社团不存在或未开放: id=" + clubId);
            }
            JointActivityJoin join = new JointActivityJoin();
            join.setActivityId(activity.getId());
            join.setClubId(clubId);
            join.setJoinStatus(JointJoinStatus.WAIT_CONFIRM.getCode());
            join.setCreateTime(LocalDateTime.now());
            joinMapper.insert(join);
            if (invited.getLeaderId() != null) {
                notifiedLeaderIds.add(invited.getLeaderId());
            }
        }
        // 推送待办消息给受邀社团负责人
        messageService.send(notifiedLeaderIds, "联合活动确认邀请",
                "《" + activity.getTitle() + "》联合活动邀请贵社团参与，请前往联合活动管理页处理。",
                MessageType.TODO.getCode(), activity.getId());
    }

    @Override
    public void updateDraft(ActivityDTO dto) {
        Activity activity = requireActivity(dto.getId());
        // 数据权限：仅发起社团负责人可编辑
        authUtil.checkClubLeader(activity.getClubId());
        if (!ActivityStatus.DRAFT.getCode().equals(activity.getStatus())) {
            throw new BizException("仅草稿状态的活动可编辑");
        }
        copyDto(dto, activity);
        activity.setUpdateTime(LocalDateTime.now());
        updateById(activity);
    }

    // ==================== 状态机流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        Activity activity = requireActivity(id);
        // 数据权限：仅发起社团负责人可提交
        authUtil.checkClubLeader(activity.getClubId());

        if (activity.getIsJoint() == 1) {
            // 状态机防护：联合活动必须处于「待社团确认」状态才允许提交，防止已审批/已驳回活动状态回退
            if (!ActivityStatus.WAIT_CLUB_CONFIRM.getCode().equals(activity.getStatus())) {
                throw new BizException("当前活动状态不允许提交校方审批");
            }
            // 关键判断：任一受邀社团 join_status=0待确认 -> 不允许提交校方审批
            List<JointActivityJoin> joins = joinMapper.selectList(
                    new LambdaQueryWrapper<JointActivityJoin>().eq(JointActivityJoin::getActivityId, id));
            List<String> pendingClubs = joins.stream()
                    .filter(j -> JointJoinStatus.WAIT_CONFIRM.getCode().equals(j.getJoinStatus()))
                    .map(j -> clubMapper.selectById(j.getClubId()))
                    .filter(Objects::nonNull)
                    .map(Club::getName).toList();
            if (!pendingClubs.isEmpty()) {
                throw new BizException("以下受邀社团尚未确认参与，不能提交校方审批：" + String.join("、", pendingClubs));
            }
        } else if (!ActivityStatus.DRAFT.getCode().equals(activity.getStatus())) {
            throw new BizException("仅草稿状态的普通活动可提交审批");
        }

        activity.setStatus(ActivityStatus.WAIT_SCHOOL_APPROVE.getCode());
        activity.setUpdateTime(LocalDateTime.now());
        updateById(activity);
        // 推送待办消息给学校管理员
        messageService.send(userService.listAdminIds(), "活动审批待办",
                "活动《" + activity.getTitle() + "》已提交，等待学校审批。", MessageType.TODO.getCode(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, ActivityAuditDTO dto) {
        // 功能权限：仅管理员审批
        authUtil.checkAdmin();
        Activity activity = requireActivity(id);
        if (!ActivityStatus.WAIT_SCHOOL_APPROVE.getCode().equals(activity.getStatus())) {
            throw new BizException("该活动不在待校方审批状态");
        }
        Club initiator = clubMapper.selectById(activity.getClubId());
        Long leaderId = initiator == null ? null : initiator.getLeaderId();

        if (Boolean.TRUE.equals(dto.getApproved())) {
            activity.setStatus(ActivityStatus.IN_PROGRESS.getCode());
            activity.setCheckinCode(generateCheckinCode());
            activity.setUpdateTime(LocalDateTime.now());
            updateById(activity);
            if (leaderId != null) {
                messageService.send(List.of(leaderId), "活动审批通过",
                        "活动《" + activity.getTitle() + "》审批通过，签到码：" + activity.getCheckinCode(),
                        MessageType.NOTICE.getCode(), id);
            }
        } else {
            if (dto.getRejectReason() == null || dto.getRejectReason().isBlank()) {
                throw new BizException("驳回必须填写理由");
            }
            activity.setStatus(ActivityStatus.REJECTED.getCode());
            activity.setRejectReason(dto.getRejectReason());
            activity.setUpdateTime(LocalDateTime.now());
            updateById(activity);
            if (leaderId != null) {
                messageService.send(List.of(leaderId), "活动审批驳回",
                        "活动《" + activity.getTitle() + "》被驳回，理由：" + dto.getRejectReason(),
                        MessageType.NOTICE.getCode(), id);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jointConfirm(JointConfirmDTO dto) {
        Activity activity = requireActivity(dto.getActivityId());
        if (activity.getIsJoint() != 1) {
            throw new BizException("该活动不是联合活动");
        }
        // 数据权限：仅受邀社团的负责人能确认
        Long myClubId = authUtil.requireManagedClubId();
        JointActivityJoin join = joinMapper.selectOne(new LambdaQueryWrapper<JointActivityJoin>()
                .eq(JointActivityJoin::getActivityId, dto.getActivityId())
                .eq(JointActivityJoin::getClubId, myClubId));
        if (join == null) {
            throw new BizException(403, "无权限：贵社团不在该联合活动的受邀名单中");
        }
        if (!JointJoinStatus.WAIT_CONFIRM.getCode().equals(join.getJoinStatus())) {
            // 邀请一旦同意/拒绝即为最终态，不可更改
            throw new BizException("该邀请已处理（同意/拒绝为最终态），如需重新邀请请重新创建联合活动");
        }
        if (Boolean.TRUE.equals(dto.getAgree())) {
            join.setJoinStatus(JointJoinStatus.AGREED.getCode());
        } else {
            if (dto.getRefuseReason() == null || dto.getRefuseReason().isBlank()) {
                throw new BizException("拒绝参与必须填写理由");
            }
            join.setJoinStatus(JointJoinStatus.REFUSED.getCode());
            join.setRefuseReason(dto.getRefuseReason());
        }
        joinMapper.updateById(join);
        // 通知发起社团负责人
        Club initiator = clubMapper.selectById(activity.getClubId());
        if (initiator != null && initiator.getLeaderId() != null) {
            String action = Boolean.TRUE.equals(dto.getAgree()) ? "已同意参与" : "已拒绝参与：" + dto.getRefuseReason();
            messageService.send(List.of(initiator.getLeaderId()), "联合活动确认结果",
                    "《" + activity.getTitle() + "》：受邀社团【" + clubMapper.selectById(myClubId).getName() + "】" + action,
                    MessageType.NOTICE.getCode(), activity.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        Activity activity = requireActivity(id);
        // 发起社团负责人或管理员可结束活动
        authUtil.checkClubLeader(activity.getClubId());
        if (!ActivityStatus.IN_PROGRESS.getCode().equals(activity.getStatus())) {
            throw new BizException("仅进行中的活动可结束");
        }
        activity.setStatus(ActivityStatus.FINISHED.getCode());
        activity.setUpdateTime(LocalDateTime.now());
        updateById(activity);
    }

    // ==================== 报名 / 签到 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signup(Long activityId) {
        Long userId = authUtil.currentUserId();
        Activity activity = requireActivity(activityId);
        if (!ActivityStatus.IN_PROGRESS.getCode().equals(activity.getStatus())) {
            throw new BizException("仅进行中的活动可报名");
        }
        Long exists = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId));
        if (exists > 0) {
            throw new BizException("您已报名该活动，请勿重复报名");
        }
        // 名额校验（0=不限）
        if (activity.getCapacity() != null && activity.getCapacity() > 0) {
            long signed = signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                    .eq(ActivitySignup::getActivityId, activityId));
            if (signed >= activity.getCapacity()) {
                throw new BizException("活动名额已满");
            }
        }
        ActivitySignup signup = new ActivitySignup();
        signup.setActivityId(activityId);
        signup.setUserId(userId);
        signup.setCreateTime(LocalDateTime.now());
        signupMapper.insert(signup);
        messageService.send(List.of(userId), "报名成功通知",
                "您已成功报名活动《" + activity.getTitle() + "》，请准时参加。",
                MessageType.NOTICE.getCode(), activityId);
    }

    @Override
    public void checkin(SignupDTO dto) {
        Long userId = authUtil.currentUserId();
        Activity activity = requireActivity(dto.getActivityId());
        if (activity.getCheckinCode() == null || activity.getCheckinCode().isBlank()) {
            throw new BizException("该活动尚未开启签到");
        }
        ActivitySignup signup = signupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, dto.getActivityId())
                .eq(ActivitySignup::getUserId, userId));
        if (signup == null) {
            throw new BizException("您尚未报名该活动");
        }
        if (signup.getCheckinTime() != null) {
            throw new BizException("您已签到，请勿重复签到");
        }
        if (!activity.getCheckinCode().equalsIgnoreCase(dto.getCheckinCode() == null ? "" : dto.getCheckinCode().trim())) {
            throw new BizException("签到码错误");
        }
        signup.setCheckinTime(LocalDateTime.now());
        signupMapper.updateById(signup);
    }

    // ==================== 查询 ====================

    @Override
    public List<ActivityVO> listActivities(Integer status, Long clubId) {
        Long userId = authUtil.currentUserId();
        List<Activity> activities;
        if (authUtil.isAdmin()) {
            // 管理员：全部活动
            activities = baseMapper.selectList(buildQuery(status, clubId));
        } else if (authUtil.isLeader()) {
            // 社团负责人：我发起的 + 受邀参与的联合活动
            Long myClubId = authUtil.requireManagedClubId();
            List<Long> invitedActivityIds = joinMapper.selectList(
                            new LambdaQueryWrapper<JointActivityJoin>().eq(JointActivityJoin::getClubId, myClubId))
                    .stream().map(JointActivityJoin::getActivityId).toList();
            activities = baseMapper.selectList(buildQuery(status, clubId)).stream()
                    .filter(a -> Objects.equals(a.getClubId(), myClubId)
                            || (a.getIsJoint() == 1 && invitedActivityIds.contains(a.getId())))
                    .toList();
        } else {
            // 学生：浏览进行中/已结束的活动
            activities = baseMapper.selectList(buildQuery(status, clubId)).stream()
                    .filter(a -> ActivityStatus.IN_PROGRESS.getCode().equals(a.getStatus())
                            || ActivityStatus.FINISHED.getCode().equals(a.getStatus()))
                    .toList();
        }
        return activities.stream().map(a -> {
            ActivityVO vo = toVO(a, userId);
            // 数据权限：签到码仅发起社团负责人/管理员可见，列表同样脱敏
            maskCheckinCode(a, vo);
            return vo;
        }).toList();
    }

    private LambdaQueryWrapper<Activity> buildQuery(Integer status, Long clubId) {
        LambdaQueryWrapper<Activity> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Activity::getStatus, status);
        }
        if (clubId != null) {
            qw.eq(Activity::getClubId, clubId);
        }
        qw.orderByDesc(Activity::getCreateTime);
        return qw;
    }

    /**
     * 签到码脱敏（数据权限）：仅发起社团负责人或管理员可见，其余用户置空
     */
    private void maskCheckinCode(Activity activity, ActivityVO vo) {
        boolean initiatorOrAdmin = authUtil.isAdmin()
                || Objects.equals(activity.getClubId(), managedClubIdOrNull());
        if (!initiatorOrAdmin) {
            vo.setCheckinCode(null);
        }
    }

    @Override
    public ActivityVO detail(Long id) {
        Activity activity = requireActivity(id);
        Long userId = authUtil.currentUserId();
        ActivityVO vo = toVO(activity, userId);
        // 数据权限：签到码仅发起负责人/管理员可见
        maskCheckinCode(activity, vo);
        return vo;
    }

    @Override
    public List<ActivityVO> mySignedActivities() {
        Long userId = authUtil.currentUserId();
        List<Long> activityIds = signupMapper.selectList(
                        new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getUserId, userId))
                .stream().map(ActivitySignup::getActivityId).toList();
        if (activityIds.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectBatchIds(activityIds).stream().map(a -> {
            ActivityVO vo = toVO(a, userId);
            // 数据权限：签到码仅发起社团负责人/管理员可见，我报名列表同样脱敏
            maskCheckinCode(a, vo);
            return vo;
        }).toList();
    }

    @Override
    public java.util.Map<String, Object> clubStats(Long clubId) {
        // 社团运营统计：供AI运营分析技能复用（Tool 内不直查报名表）
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        List<Activity> clubActivities = list(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getClubId, clubId));
        long inProgress = clubActivities.stream().filter(a -> ActivityStatus.IN_PROGRESS.getCode().equals(a.getStatus())).count();
        long finished = clubActivities.stream().filter(a -> ActivityStatus.FINISHED.getCode().equals(a.getStatus())).count();
        long jointCount = clubActivities.stream().filter(a -> a.getIsJoint() != null && a.getIsJoint() == 1).count();
        long signupTotal = 0;
        long checkedTotal = 0;
        for (Activity a : clubActivities) {
            List<ActivitySignup> signups = signupMapper.selectList(
                    new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getActivityId, a.getId()));
            signupTotal += signups.size();
            checkedTotal += signups.stream().filter(s -> s.getCheckinTime() != null).count();
        }
        stats.put("activityTotal", clubActivities.size());
        stats.put("inProgress", inProgress);
        stats.put("finished", finished);
        stats.put("jointActivityTotal", jointCount);
        stats.put("signupTotal", signupTotal);
        stats.put("checkedTotal", checkedTotal);
        stats.put("checkinRate", signupTotal == 0 ? "0%"
                : Math.round(checkedTotal * 100.0 / signupTotal) + "%");
        return stats;
    }

    /**
     * 活动报名列表（核心数据权限控制，手动过滤）：
     * - 管理员 / 发起社团负责人：查看全部报名数据
     * - 受邀社团负责人：仅能看到本社团成员的报名数据
     * - 普通学生：仅能看到本人的报名记录
     */
    @Override
    public List<SignupVO> listSignups(Long activityId) {
        Activity activity = requireActivity(activityId);
        Long userId = authUtil.currentUserId();
        List<ActivitySignup> signups = signupMapper.selectList(
                new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getActivityId, activityId));

        if (authUtil.isAdmin() || Objects.equals(activity.getClubId(), managedClubIdOrNull())) {
            // 全量可见
        } else if (authUtil.isLeader()) {
            // 受邀社团负责人：数据过滤 -> 只保留本社团成员的报名
            Long myClubId = authUtil.requireManagedClubId();
            List<Long> myMemberUserIds = clubMemberMapper.selectList(
                            new LambdaQueryWrapper<ClubMember>()
                                    .eq(ClubMember::getClubId, myClubId)
                                    .eq(ClubMember::getStatus, ClubMemberServiceImpl.MEMBER_NORMAL))
                    .stream().map(ClubMember::getUserId).toList();
            signups = signups.stream().filter(s -> myMemberUserIds.contains(s.getUserId())).toList();
        } else {
            // 普通学生：只能看本人记录
            signups = signups.stream().filter(s -> s.getUserId().equals(userId)).toList();
        }
        return signups.stream().map(this::toSignupVO).toList();
    }

    /**
     * 统计活动报名总人数（聚合计数，不按角色过滤）。
     * <p>实现说明：直接对 activity_signup 表按 activityId 做 SELECT COUNT(*) 聚合，
     * 不经过任何角色数据权限过滤。<b>刻意不复用 {@link #listSignups(Long)}</b>：
     * listSignups 按角色过滤明细（普通学生角色下仅返回本人报名记录，最多 0/1 条），
     * 若用其 size() 统计报名人数，学生视角会得到严重失真的"剩余名额"
     * （例如真实已报 28/30，学生却看到"已报 0/30、剩余 30 人"）。
     * 报名人数/剩余名额属活动公开信息，所有角色应看到同一真实总数。</p>
     */
    @Override
    public int countSignups(Long activityId) {
        // activityId 为空无法定位活动，直接返回 0（防御性兜底，不抛异常）
        if (activityId == null) {
            return 0;
        }
        long c = signupMapper.selectCount(
                new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getActivityId, activityId));
        return (int) c;
    }

    // ==================== 私有工具 ====================

    /** 解析当前用户的发起社团ID（负责人身份校验） */
    private Long resolveInitiatorClubId() {
        if (authUtil.isAdmin()) {
            throw new BizException("管理员不创建活动，请由社团负责人发起");
        }
        return authUtil.requireManagedClubId();
    }

    /** 当前用户管理的社团ID（可为null） */
    private Long managedClubIdOrNull() {
        try {
            return authUtil.requireManagedClubId();
        } catch (BizException e) {
            return null;
        }
    }

    private Activity requireActivity(Long id) {
        Activity activity = getById(id);
        if (activity == null) {
            throw new BizException("活动不存在");
        }
        return activity;
    }

    private void copyDto(ActivityDTO dto, Activity activity) {
        activity.setTitle(dto.getTitle());
        activity.setLocation(dto.getLocation());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setCapacity(dto.getCapacity() == null ? 0 : dto.getCapacity());
        activity.setIntro(dto.getIntro());
        activity.setSafetyOfficer(dto.getSafetyOfficer());
    }

    /** 生成4位数字签到码（简单实现） */
    private String generateCheckinCode() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    /** 实体转VO（附加社团名、状态描述、联合确认状态、当前用户报名情况） */
    private ActivityVO toVO(Activity a, Long currentUserId) {
        ActivityVO vo = new ActivityVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setIsJoint(a.getIsJoint());
        vo.setClubId(a.getClubId());
        Club club = clubMapper.selectById(a.getClubId());
        vo.setClubName(club == null ? null : club.getName());
        vo.setLocation(a.getLocation());
        vo.setStartTime(a.getStartTime() == null ? null : a.getStartTime().format(FMT));
        vo.setEndTime(a.getEndTime() == null ? null : a.getEndTime().format(FMT));
        vo.setCapacity(a.getCapacity());
        vo.setIntro(a.getIntro());
        vo.setSafetyOfficer(a.getSafetyOfficer());
        vo.setStatus(a.getStatus());
        vo.setStatusDesc(ActivityStatus.of(a.getStatus()).getDesc());
        vo.setRejectReason(a.getRejectReason());
        vo.setCheckinCode(a.getCheckinCode());
        vo.setSummary(a.getSummary());
        vo.setCreatorId(a.getCreatorId());
        // 联合活动：附带各受邀社团确认状态
        if (a.getIsJoint() != null && a.getIsJoint() == 1) {
            List<JointActivityJoin> joins = joinMapper.selectList(
                    new LambdaQueryWrapper<JointActivityJoin>().eq(JointActivityJoin::getActivityId, a.getId()));
            Map<Long, Club> clubMap = joins.isEmpty() ? Map.of()
                    : clubMapper.selectBatchIds(joins.stream().map(JointActivityJoin::getClubId).toList())
                    .stream().collect(Collectors.toMap(Club::getId, Function.identity()));
            vo.setJointJoins(joins.stream().map(j -> {
                ActivityVO.JointJoinVO jv = new ActivityVO.JointJoinVO();
                jv.setId(j.getId());
                jv.setClubId(j.getClubId());
                Club c = clubMap.get(j.getClubId());
                jv.setClubName(c == null ? null : c.getName());
                jv.setJoinStatus(j.getJoinStatus());
                jv.setJoinStatusDesc(JointJoinStatus.of(j.getJoinStatus()).getDesc());
                jv.setRefuseReason(j.getRefuseReason());
                return jv;
            }).toList());
        }
        // 当前用户是否已报名
        if (currentUserId != null) {
            vo.setSigned(signupMapper.selectCount(new LambdaQueryWrapper<ActivitySignup>()
                    .eq(ActivitySignup::getActivityId, a.getId())
                    .eq(ActivitySignup::getUserId, currentUserId)) > 0);
        }
        return vo;
    }

    private SignupVO toSignupVO(ActivitySignup s) {
        SignupVO vo = new SignupVO();
        vo.setId(s.getId());
        vo.setActivityId(s.getActivityId());
        vo.setUserId(s.getUserId());
        SysUser user = userMapper.selectById(s.getUserId());
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setStudentNo(user.getStudentNo());
        }
        // 报名学生所属社团名称（取其第一个正常加入的社团）
        ClubMember firstMember = clubMemberMapper.selectOne(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getUserId, s.getUserId())
                .eq(ClubMember::getStatus, ClubMemberServiceImpl.MEMBER_NORMAL)
                .last("LIMIT 1"));
        if (firstMember != null) {
            Club c = clubMapper.selectById(firstMember.getClubId());
            vo.setClubName(c == null ? null : c.getName());
        }
        vo.setChecked(s.getCheckinTime() != null ? 1 : 0);
        return vo;
    }
}
