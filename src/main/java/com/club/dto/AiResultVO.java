package com.club.dto;

import lombok.Data;

/** AI 智能体调用出参 */
@Data
public class AiResultVO {

    /** 调用记录ID（ai_generate_record.id） */
    private Long recordId;
    /** 内容来源：AI大模型 / 本地降级模板 */
    private String source;
    /** 生成的内容（用户确认编辑后回填，不直接入库） */
    private String content;
}
