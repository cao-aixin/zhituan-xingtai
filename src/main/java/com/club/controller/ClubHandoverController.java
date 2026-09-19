package com.club.controller;

import com.club.dto.HandoverDTO;
import com.club.dto.HandoverVO;
import com.club.service.ClubHandoverService;
import com.club.util.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社团换届接口（移植自旧版 zhituan-system）
 */
@RestController
@RequestMapping("/api/handover")
public class ClubHandoverController {

    private final ClubHandoverService clubHandoverService;

    public ClubHandoverController(ClubHandoverService clubHandoverService) {
        this.clubHandoverService = clubHandoverService;
    }

    /** 发起换届：body {clubId, newLeaderStudentNo, note} */
    @PostMapping
    public Result<Void> handover(@Valid @RequestBody HandoverDTO dto) {
        clubHandoverService.handover(dto);
        return Result.ok();
    }

    /** 换届记录列表（clubId 可选；数据权限：管理员全部、负责人本社团） */
    @GetMapping("/list")
    public Result<List<HandoverVO>> list(@RequestParam(required = false) Long clubId) {
        return Result.ok(clubHandoverService.list(clubId));
    }
}
