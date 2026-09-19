package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.dto.ActivityAuditDTO;
import com.club.dto.ActivityDTO;
import com.club.dto.ActivityVO;
import com.club.dto.JointConfirmDTO;
import com.club.dto.SignupDTO;
import com.club.dto.SignupVO;
import com.club.entity.Activity;

import java.util.List;

/** 活动服务（普通活动 + 联合活动） */
public interface ActivityService extends IService<Activity> {

    /** 创建活动：普通活动入草稿；联合活动直接进入「待社团确认」并推送待办消息 */
    Long create(ActivityDTO dto);

    /** 编辑活动（草稿状态可改，发起负责人） */
    void updateDraft(ActivityDTO dto);

    /** 提交审批：普通活动 草稿->待校方审批；联合活动需全部受邀社团确认后才能提交 */
    void submit(Long id);

    /** 管理员审批（通过：进行中+生成签到码；驳回：已驳回+理由） */
    void audit(Long id, ActivityAuditDTO dto);

    /** 活动列表（按状态/社团过滤，区分发起与受邀数据权限） */
    List<ActivityVO> listActivities(Integer status, Long clubId);

    /** 活动详情（含联合确认状态、当前用户报名情况） */
    ActivityVO detail(Long id);

    /** 学生报名 */
    void signup(Long activityId);

    /** 学生填签到码签到 */
    void checkin(SignupDTO dto);

    /** 活动报名列表（数据权限：管理员/发起负责人看全部，受邀负责人仅本社团成员，学生仅本人） */
    List<SignupVO> listSignups(Long activityId);

    /**
     * 统计活动报名总人数（聚合计数，不按角色过滤）。
     * <p>口径说明：报名人数/剩余名额属于活动公开信息，学生视角的问答（如"还剩几个名额"）
     * 必须拿到真实报名总数才有意义。<b>不可复用 {@link #listSignups(Long)}</b>：
     * listSignups 按角色做数据权限过滤（普通学生角色下仅返回本人报名，最多 0/1 条），
     * 若用它统计人数会导致学生视角"剩余名额"严重失真。本方法直接对报名表做聚合计数，
     * 任何角色调用结果一致。</p>
     *
     * @param activityId 活动ID
     * @return 该活动的报名总人数；activityId 为空时返回 0
     */
    int countSignups(Long activityId);

    /** 结束活动（发起负责人/管理员，进行中->已结束） */
    void finish(Long id);

    /** 受邀社团负责人：同意/拒绝参与联合活动 */
    void jointConfirm(JointConfirmDTO dto);

    /** 我报名的活动（学生） */
    List<ActivityVO> mySignedActivities();

    /** 社团运营统计（AI分析Tool复用）：活动数/报名数/签到数等 */
    java.util.Map<String, Object> clubStats(Long clubId);
}
