package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用记录表：Agent 每次调用写一条，便于审计与排查
 */
@Data
@TableName("ai_generate_record")
public class AiGenerateRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 调用人 */
    private Long userId;
    /** 技能名称（见 AiSkillName 枚举） */
    private String skillName;
    /** 发送给大模型的完整提示词 */
    private String agentPrompt;
    /** 上下文参数（JSON） */
    private String contextParam;
    /** 生成结果内容 */
    private String resultContent;
    /** 调用状态：1成功 0失败 2降级 */
    private Integer callStatus;
    /** 失败/降级原因 */
    private String errorMsg;
    private LocalDateTime createTime;
}
