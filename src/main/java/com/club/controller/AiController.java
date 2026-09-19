package com.club.controller;

import com.club.dto.AiRequestDTO;
import com.club.dto.AiResultVO;
import com.club.agent.AgentService;
import com.club.entity.AiGenerateRecord;
import com.club.service.AiRecordService;
import com.club.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 智能体接口：统一入口（同步调用，异常降级不阻断业务）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AgentService agentService;
    private final AiRecordService aiRecordService;

    public AiController(AgentService agentService, AiRecordService aiRecordService) {
        this.agentService = agentService;
        this.aiRecordService = aiRecordService;
    }

    /**
     * AI 生成（统一入口）
     * body: {skill: ACTIVITY_PLAN/ACTIVITY_SUMMARY/CLUB_ANALYSE/ACTIVITY_RECOMMEND,
     *        activityId, clubId, extraInput}
     * 返回内容仅供用户预览编辑，确认后由前端回填表单提交保存，不直接入库。
     */
    @PostMapping("/generate")
    public Result<AiResultVO> generate(@RequestBody AiRequestDTO dto) {
        return Result.ok(agentService.invoke(dto));
    }

    /** 我的AI调用记录（最近50条） */
    @GetMapping("/record/list")
    public Result<List<AiGenerateRecord>> records() {
        return Result.ok(aiRecordService.myRecords());
    }
}
