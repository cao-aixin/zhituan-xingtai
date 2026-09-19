package com.club.controller;

import com.club.dto.ActivityAuditDTO;
import com.club.dto.ActivityDTO;
import com.club.dto.ActivityVO;
import com.club.dto.JointConfirmDTO;
import com.club.dto.SignupDTO;
import com.club.dto.SignupVO;
import com.club.service.ActivityService;
import com.club.util.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动接口：普通活动 + 联合活动全流程
 */
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** 创建活动（isJoint=1为联合活动，需传inviteClubIds；创建成功返回活动ID） */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ActivityDTO dto) {
        return Result.ok(activityService.create(dto));
    }

    /** 编辑草稿 */
    @PutMapping
    public Result<Void> updateDraft(@Valid @RequestBody ActivityDTO dto) {
        activityService.updateDraft(dto);
        return Result.ok();
    }

    /** 提交校方审批（联合活动需全部受邀社团确认后才能提交） */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        activityService.submit(id);
        return Result.ok();
    }

    /** 管理员审批：body {approved:true/false, rejectReason} */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody ActivityAuditDTO dto) {
        activityService.audit(id, dto);
        return Result.ok();
    }

    /** 受邀社团：同意/拒绝参与联合活动 body {activityId, agree, refuseReason} */
    @PostMapping("/joint-confirm")
    public Result<Void> jointConfirm(@RequestBody JointConfirmDTO dto) {
        activityService.jointConfirm(dto);
        return Result.ok();
    }

    /** 活动列表（status/clubId可选过滤，自动按角色做数据过滤） */
    @GetMapping("/list")
    public Result<List<ActivityVO>> list(@RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) Long clubId) {
        return Result.ok(activityService.listActivities(status, clubId));
    }

    /** 活动详情（含联合确认状态） */
    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    /** 学生报名 */
    @PostMapping("/{id}/signup")
    public Result<Void> signup(@PathVariable Long id) {
        activityService.signup(id);
        return Result.ok();
    }

    /** 学生填签到码签到 body {activityId, checkinCode} */
    @PostMapping("/signup/checkin")
    public Result<Void> checkin(@RequestBody SignupDTO dto) {
        activityService.checkin(dto);
        return Result.ok();
    }

    /** 活动报名列表（数据权限：受邀负责人仅见本社团成员） */
    @GetMapping("/{id}/signups")
    public Result<List<SignupVO>> signups(@PathVariable Long id) {
        return Result.ok(activityService.listSignups(id));
    }

    /** 我报名的活动（学生） */
    @GetMapping("/my-signed")
    public Result<List<ActivityVO>> mySigned() {
        return Result.ok(activityService.mySignedActivities());
    }

    /** 结束活动（发起负责人/管理员；结束后可调AI生成总结） */
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        activityService.finish(id);
        return Result.ok();
    }
}
