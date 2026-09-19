package com.club.controller;

import com.club.dto.ClubDTO;
import com.club.entity.Club;
import com.club.service.ClubService;
import com.club.util.Result;
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
 * 社团接口：浏览、申请创建、审核、我管理的社团
 */
@RestController
@RequestMapping("/api/club")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    /** 社团列表（学生仅见正常社团；管理员见全部；keyword按名称搜索） */
    @GetMapping("/list")
    public Result<List<Club>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(clubService.listClubs(keyword));
    }

    /** 社团详情 */
    @GetMapping("/{id}")
    public Result<Club> detail(@PathVariable Long id) {
        return Result.ok(clubService.detail(id));
    }

    /** 学生申请创建社团（进入待审核） */
    @PostMapping("/apply")
    public Result<Void> apply(@RequestBody ClubDTO dto) {
        clubService.apply(dto);
        return Result.ok();
    }

    /** 待审核社团列表（仅管理员） */
    @GetMapping("/pending")
    public Result<List<Club>> pending() {
        return Result.ok(clubService.listPending());
    }

    /** 社团申请审核（仅管理员）：body传 {id, auditResult:1通过/2驳回, rejectReason} */
    @PutMapping("/audit")
    public Result<Void> audit(@RequestBody ClubDTO dto) {
        clubService.audit(dto);
        return Result.ok();
    }

    /** 我管理的社团（负责人） */
    @GetMapping("/my")
    public Result<List<Club>> my() {
        return Result.ok(clubService.myManagedClubs());
    }
}
