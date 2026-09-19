package com.club.agent;

import com.club.agent.tools.ActivityQaTool;
import com.club.agent.tools.ActivityRecommendTool;
import com.club.agent.tools.ClubAnalyseTool;
import com.club.agent.tools.DocumentGenerateTool;
import com.club.agent.tools.RiskInspectTool;
import com.club.dto.AiRequestDTO;
import com.club.dto.AiResultVO;
import com.club.entity.Activity;
import com.club.entity.AiGenerateRecord;
import com.club.entity.Club;
import com.club.enums.AiSkillName;
import com.club.mapper.AiGenerateRecordMapper;
import com.club.service.ActivityService;
import com.club.service.ClubService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 统一入口服务【重点】
 * - 按「技能名称」分发到对应 @Tool 技能，走 Spring AI Tool Call 工具调用
 * - 同步调用（不做MQ），Agent 无会话记忆（每次独立上下文）
 * - 每次调用写一条 ai_generate_record
 * - 异常降级优先：api-key 为空 / 调用失败 / 超时 -> 降级为本地模板生成，返回内容并标注来源，绝不抛500
 * - AI 生成内容不直接入库：只返回给前端，用户确认编辑后回填保存
 */
@Slf4j
@Service
public class AgentService {

    /** 来源标识 */
    public static final String SOURCE_AI = "AI大模型";
    public static final String SOURCE_FALLBACK = "本地降级模板";

    /** 调用状态：1成功 0失败 2降级 */
    private static final int STATUS_OK = 1;
    private static final int STATUS_FALLBACK = 2;
    private static final int STATUS_FAIL = 0;

    private final ChatClient chatClient;
    private final DocumentGenerateTool documentGenerateTool;
    private final ClubAnalyseTool clubAnalyseTool;
    private final ActivityRecommendTool activityRecommendTool;
    /** 活动风险巡检技能（管理员审批辅助） */
    private final RiskInspectTool riskInspectTool;
    /** 活动问答助手技能（学生端对话） */
    private final ActivityQaTool activityQaTool;
    private final ActivityService activityService;
    private final ClubService clubService;
    private final AiGenerateRecordMapper aiRecordMapper;
    private final AuthUtil authUtil;
    private final AiPermissionChecker aiPermissionChecker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${club.ai-fallback-enabled:true}")
    private boolean fallbackEnabled;

    /**
     * 构造：api-key 非空时手工构建 Spring AI ChatClient（OpenAI 兼容接口）。
     * 未配置 api-key / 构建失败时 chatClient 为 null，运行期自动走降级路径，不影响启动。
     */
    public AgentService(@Value("${spring.ai.openai.api-key:}") String apiKey,
                        @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
                        @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
                        @Value("${spring.ai.openai.chat.options.temperature:0.7}") Double temperature,
                        DocumentGenerateTool documentGenerateTool,
                        ClubAnalyseTool clubAnalyseTool,
                        ActivityRecommendTool activityRecommendTool,
                        RiskInspectTool riskInspectTool,
                        ActivityQaTool activityQaTool,
                        ActivityService activityService,
                        ClubService clubService,
                        AiGenerateRecordMapper aiRecordMapper,
                        AuthUtil authUtil,
                        AiPermissionChecker aiPermissionChecker) {
        this.documentGenerateTool = documentGenerateTool;
        this.clubAnalyseTool = clubAnalyseTool;
        this.activityRecommendTool = activityRecommendTool;
        this.riskInspectTool = riskInspectTool;
        this.activityQaTool = activityQaTool;
        this.activityService = activityService;
        this.clubService = clubService;
        this.aiRecordMapper = aiRecordMapper;
        this.authUtil = authUtil;
        this.aiPermissionChecker = aiPermissionChecker;
        ChatClient client = null;
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                OpenAiApi api = OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .build();
                OpenAiChatModel chatModel = OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(OpenAiChatOptions.builder()
                                .model(model)
                                .temperature(temperature)
                                .build())
                        .build();
                client = ChatClient.builder(chatModel).build();
            } catch (Exception e) {
                log.warn("Spring AI ChatClient 构建失败，AI功能将走本地降级模板: {}", e.getMessage());
            }
        }
        this.chatClient = client;
    }

    /**
     * Agent 统一调用入口（Controller 唯一依赖）
     */
    public AiResultVO invoke(AiRequestDTO dto) {
        AiSkillName skill = AiSkillName.of(dto.getSkill());

        // 权限前置校验：必须在调用大模型之前完成。
        // 原因：@Tool 内部抛出的 BizException 会被 Spring AI 捕获转成错误文本喂回 LLM，
        // 模型会把它组织成「无权访问」的文案并按成功返回 200，导致 API 契约失真。
        // 这里提前校验，不通过直接抛 403，并记一条失败调用记录，不进入模型调用。
        try {
            aiPermissionChecker.check(skill, dto);
        } catch (BizException be) {
            saveRecord(skill, null, null, null, STATUS_FAIL, be.getMessage());
            throw be;
        }

        String contextJson = buildContextJson(dto, skill);
        String prompt = buildPrompt(dto, skill);

        String content;
        String source;
        int status;
        String errorMsg = null;

        if (chatClient == null) {
            // 未配置 api-key：直接降级
            if (!fallbackEnabled) {
                throw new BizException("AI智能服务暂时繁忙，请稍后重试");
            }
            content = cleanMarkdown(localFallback(skill, dto));
            source = SOURCE_FALLBACK;
            status = STATUS_FALLBACK;
            errorMsg = "未配置 spring.ai.openai.api-key，已降级为本地模板生成";
        } else {
            try {
                // 同步调用大模型，挂载对应技能的 @Tool 工具（Tool Call 循环由 Spring AI 完成）
                content = chatClient.prompt()
                        .system("你是校园社团管理系统的AI助手，必须调用提供的工具技能完成任务，" +
                                "工具内部会做权限校验，不得编造数据。输出工具结果整理后的最终文案。" +
                                "注意：输出必须是纯文本，禁止使用任何 Markdown 语法" +
                                "（如 **加粗**、## 标题、* 斜体、- 列表符号等），" +
                                "小标题直接用「【标题】」的中文括号形式。")
                        .user(prompt)
                        .tools(toolBeanFor(skill))
                        .call()
                        .content();
                // 清洗 Markdown 残留：即使提示词约束了，大模型仍可能输出 **、## 等标记，
                // 回填到表单是纯文本场景，这些符号会原样显示影响观感，这里统一兜底清洗。
                content = cleanMarkdown(content);
                source = SOURCE_AI;
                status = STATUS_OK;
            } catch (BizException be) {
                // 技能内部权限/参数校验失败：属于业务错误，直接返回，不走降级
                throw be;
            } catch (Exception e) {
                // AI 调用失败/超时：捕获降级，绝不抛500，不阻断业务
                log.warn("AI调用失败，触发降级。skill={}, err={}", skill.getCode(), e.getMessage());
                if (!fallbackEnabled) {
                    throw new BizException("AI智能服务暂时繁忙，请稍后重试");
                }
                content = cleanMarkdown(localFallback(skill, dto));
                source = SOURCE_FALLBACK;
                status = STATUS_FALLBACK;
                errorMsg = "AI调用失败: " + e.getMessage();
            }
        }

        // 每次调用写 ai_generate_record
        Long recordId = saveRecord(skill, prompt, contextJson, content, status, errorMsg);

        AiResultVO vo = new AiResultVO();
        vo.setRecordId(recordId);
        vo.setSource(source);
        vo.setContent(content);
        return vo;
    }

    // ==================== 提示词与上下文 ====================

    /**
     * 清洗 AI 输出中的 Markdown 标记（纯文本兜底）。
     * 背景：文案回填到表单/介绍框是纯文本场景，大模型习惯输出 Markdown
     * （**加粗**、## 标题、* 斜体、`代码`、~~删除线~~、[文本](链接) 等），
     * 这些符号会以原样显示很难看。即使提示词已约束，仍可能残留，这里统一清洗。
     * 注意保留中文正常符号（【】、·、→、——、1. 编号等），只去 Markdown 语法标记。
     */
    static String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String s = text
                // **加粗** / __加粗__ -> 保留内部文字
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                // 行首 Markdown 标题：## 标题 -> 标题
                .replaceAll("(?m)^#{1,6}\\s*", "")
                // 行首列表符号：- 项目 / * 项目 / + 项目 -> 项目（保留 1. 数字编号）
                .replaceAll("(?m)^[ \\t]*[-*+][ \\t]+", "")
                // 单星号斜体（在去粗后处理，避免与加粗冲突）
                .replaceAll("(?<!\\*)\\*(?!\\*)([^*\\n]+?)\\*(?!\\*)", "$1")
                // `代码` / ```代码块``` -> 保留内部文字
                .replaceAll("`{1,3}([^`]*)`{1,3}", "$1")
                // ~~删除线~~ -> 保留内部文字
                .replaceAll("~~(.+?)~~", "$1")
                // [文本](链接) -> 文本
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        // 连续空行压缩为最多一个空行，避免清洗后出现大段空白
        return s.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** 按技能组装上下文参数 JSON（入库存档） */
    private String buildContextJson(AiRequestDTO dto, AiSkillName skill) {
        try {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("skill", skill.getCode());
            ctx.put("userId", authUtil.currentUserId());
            ctx.put("activityId", dto.getActivityId());
            ctx.put("clubId", dto.getClubId());
            ctx.put("extraInput", dto.getExtraInput());
            // 扩展字段：仅在传值时写入，保持旧技能的存档结构不变
            if (dto.getStyle() != null) {
                ctx.put("style", dto.getStyle());
            }
            if (dto.getClubIds() != null && !dto.getClubIds().isEmpty()) {
                ctx.put("clubIds", dto.getClubIds());
                ctx.put("clubNames", resolveClubNames(dto.getClubIds()));
            }
            if (dto.getText() != null && !dto.getText().isBlank()) {
                // 通知草稿仅存档，避免过长；此处完整保留便于问题回溯
                ctx.put("text", dto.getText());
            }
            return objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 按技能组装发送给大模型的提示词（含真实业务上下文，减少幻觉） */
    private String buildPrompt(AiRequestDTO dto, AiSkillName skill) {
        Long userId = authUtil.currentUserId();
        return switch (skill) {
            case ACTIVITY_PLAN -> {
                String theme = dto.getExtraInput() == null || dto.getExtraInput().isBlank()
                        ? "校园主题活动" : dto.getExtraInput();
                if (dto.getActivityId() != null) {
                    Activity a = activityService.getById(dto.getActivityId());
                    if (a != null) {
                        theme = a.getTitle();
                    }
                }
                Club club = resolveUserClub();
                yield "请调用 generateActivityPlan 工具，为社团「" + (club == null ? "本校社团" : club.getName())
                        + "」生成主题为「" + theme + "」的活动方案文案。";
            }
            case ACTIVITY_SUMMARY -> "请调用 generateActivitySummary 工具，为活动ID=" + dto.getActivityId()
                    + " 的活动生成活动总结文案。" + (dto.getExtraInput() == null ? "" : "补充要求：" + dto.getExtraInput());
            case CLUB_ANALYSE -> {
                Long clubId = dto.getClubId() != null ? dto.getClubId() : resolveUserClubId();
                yield "请调用 analyseClub 工具，分析社团ID=" + clubId + " 的运营情况。"
                        + (dto.getExtraInput() == null ? "" : "重点关注：" + dto.getExtraInput());
            }
            case ACTIVITY_RECOMMEND -> "请调用 recommendActivities 工具，为当前登录学生生成个性化活动推荐。"
                    + (dto.getExtraInput() == null ? "" : "用户偏好：" + dto.getExtraInput());
            // ==================== 扩展技能提示词 ====================
            case RISK_INSPECT -> "请调用 inspectActivityRisk 工具，对活动ID=" + dto.getActivityId()
                    + " 的待审批活动做风险巡检，输出风险清单与处置建议。"
                    + (dto.getExtraInput() == null ? "" : "管理员关注重点：" + dto.getExtraInput());
            case NOTICE_POLISH -> {
                String draft = dto.getText() == null ? "" : dto.getText();
                String style = dto.getStyle() == null || dto.getStyle().isBlank() ? "正式" : dto.getStyle();
                yield "请调用 polishNotice 工具，把下面的通知草稿改写为「" + style + "」风格，"
                        + "必须保留原文的时间、地点、参与要求等关键信息。\n通知草稿原文：\n" + draft;
            }
            case JOINT_PLAN -> {
                String theme = dto.getExtraInput() == null || dto.getExtraInput().isBlank()
                        ? "联合主题活动" : dto.getExtraInput();
                String clubs = String.join("、", resolveClubNames(dto.getClubIds()));
                yield "请调用 generateJointPlan 工具，为联合活动主题「" + theme + "」生成策划方案，"
                        + "受邀社团：" + (clubs.isEmpty() ? "暂无" : clubs) + "。"
                        + "需要包含主题定位、各社团分工、筹备时间线、宣传方案与安全应急预案。";
            }
            case ACTIVITY_QA -> "请调用 answerActivityQuestion 工具，基于当前用户有权限查看的真实数据回答提问。"
                    + "用户提问：" + (dto.getExtraInput() == null ? "" : dto.getExtraInput());
        };
    }

    /** 社团ID列表 -> 社团名称列表（查不到时跳过，避免提示词与实际不符） */
    private List<String> resolveClubNames(List<Long> clubIds) {
        if (clubIds == null || clubIds.isEmpty()) {
            return List.of();
        }
        return clubIds.stream()
                .map(clubService::getById)
                .filter(c -> c != null)
                .map(Club::getName)
                .toList();
    }

    /** 风格归一化（通知润色）：非「正式/亲切/紧急」统一按「正式」处理 */
    private String normalizeStyle(String style) {
        if (style == null || style.isBlank()) {
            return "正式";
        }
        String s = style.trim();
        if (s.contains("亲切")) {
            return "亲切";
        }
        if (s.contains("紧急")) {
            return "紧急";
        }
        return "正式";
    }

    /** 当前用户管理的社团（发起负责人） */
    private Club resolveUserClub() {
        Long clubId = resolveUserClubId();
        return clubId == null ? null : clubService.getById(clubId);
    }

    private Long resolveUserClubId() {
        try {
            return authUtil.requireManagedClubId();
        } catch (BizException e) {
            return null;
        }
    }

    /** 技能 -> 对应 Tool Bean（Tool Call 时 Spring AI 反射调用 @Tool 方法） */
    private Object toolBeanFor(AiSkillName skill) {
        return switch (skill) {
            case ACTIVITY_PLAN, ACTIVITY_SUMMARY, NOTICE_POLISH, JOINT_PLAN -> documentGenerateTool;
            case CLUB_ANALYSE -> clubAnalyseTool;
            case ACTIVITY_RECOMMEND -> activityRecommendTool;
            case RISK_INSPECT -> riskInspectTool;
            case ACTIVITY_QA -> activityQaTool;
        };
    }

    // ==================== 本地降级模板 ====================

    /**
     * 本地降级：不走大模型，直接在本地调用同一套 Tool 方法（内部逻辑一致，
     * 权限校验同样生效），保证降级内容和 AI 版本数据口径一致。
     */
    private String localFallback(AiSkillName skill, AiRequestDTO dto) {
        String extra = dto.getExtraInput();
        try {
            return switch (skill) {
                case ACTIVITY_PLAN -> {
                    String theme = extra == null || extra.isBlank() ? "校园主题活动" : extra;
                    if (dto.getActivityId() != null) {
                        Activity a = activityService.getById(dto.getActivityId());
                        if (a != null) {
                            theme = a.getTitle();
                        }
                    }
                    Club club = resolveUserClub();
                    yield documentGenerateTool.generateActivityPlan(theme,
                            club == null ? null : club.getName(), extra);
                }
                case ACTIVITY_SUMMARY -> documentGenerateTool.generateActivitySummary(dto.getActivityId(), extra);
                case CLUB_ANALYSE -> clubAnalyseTool.analyseClub(
                        dto.getClubId() != null ? dto.getClubId() : resolveUserClubId(), extra);
                case ACTIVITY_RECOMMEND -> activityRecommendTool.recommendActivities(extra);
                // ==================== 扩展技能本地降级 ====================
                // 降级时直接调用同一套 Tool 方法，保证与 AI 版本数据口径、权限校验完全一致
                case RISK_INSPECT -> riskInspectTool.inspectActivityRisk(dto.getActivityId(), extra);
                case NOTICE_POLISH -> documentGenerateTool.polishNotice(dto.getText(), dto.getStyle(), extra);
                case JOINT_PLAN -> {
                    String theme = extra == null || extra.isBlank() ? "联合主题活动" : extra;
                    yield documentGenerateTool.generateJointPlan(theme,
                            String.join("、", resolveClubNames(dto.getClubIds())), extra);
                }
                case ACTIVITY_QA -> activityQaTool.answerActivityQuestion(
                        extra == null || extra.isBlank() ? "有什么活动可以参加？" : extra);
            };
        } catch (BizException be) {
            // 权限/参数问题属于业务错误，原样抛出
            throw be;
        } catch (Exception e) {
            log.warn("本地降级模板生成失败 skill={}", skill.getCode(), e);
            throw new BizException("AI智能服务暂时繁忙，请稍后重试");
        }
    }

    /** 写 AI 调用记录 */
    private Long saveRecord(AiSkillName skill, String prompt, String contextJson,
                            String content, int status, String errorMsg) {
        AiGenerateRecord record = new AiGenerateRecord();
        record.setUserId(authUtil.currentUserId());
        record.setSkillName(skill.getCode());
        record.setAgentPrompt(prompt);
        record.setContextParam(contextJson);
        record.setResultContent(content);
        record.setCallStatus(status);
        record.setErrorMsg(errorMsg);
        record.setCreateTime(LocalDateTime.now());
        aiRecordMapper.insert(record);
        return record.getId();
    }
}
