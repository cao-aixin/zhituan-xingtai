package com.club.controller;

import com.club.service.ClubMemberService;
import com.club.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 社团成员接口：入社/退社/审核/成员列表
 */
@RestController
@RequestMapping("/api/club")
public class ClubMemberController {

    private final ClubMemberService clubMemberService;

    public ClubMemberController(ClubMemberService clubMemberService) {
        this.clubMemberService = clubMemberService;
    }

    /** 社团成员列表（仅本社团负责人/管理员） */
    @GetMapping("/{clubId}/member/list")
    public Result<List<Map<String, Object>>> members(@PathVariable Long clubId) {
        return Result.ok(clubMemberService.listMembers(clubId));
    }

    /** 学生申请入社 */
    @PostMapping("/{clubId}/member/join")
    public Result<Void> join(@PathVariable Long clubId) {
        clubMemberService.join(clubId);
        return Result.ok();
    }

    /** 学生退出社团 */
    @PostMapping("/{clubId}/member/quit")
    public Result<Void> quit(@PathVariable Long clubId) {
        clubMemberService.quit(clubId);
        return Result.ok();
    }

    /** 入社申请审核（负责人）：approved=true通过 / false驳回 */
    @PutMapping("/member/{memberId}/audit")
    public Result<Void> audit(@PathVariable Long memberId,
                              @RequestParam boolean approved) {
        clubMemberService.auditMember(memberId, approved);
        return Result.ok();
    }
}
