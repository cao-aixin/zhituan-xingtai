package com.club.dto;

import lombok.Data;

/** 社团入参（申请创建） */
@Data
public class ClubDTO {

    private Long id;
    /** 社团名称 */
    private String name;
    /** 社团简介 */
    private String intro;
    /** 标签（逗号分隔） */
    private String tags;
    /** 审批结果：1通过 2驳回（管理员审核用） */
    private Integer auditResult;
    /** 驳回理由 */
    private String rejectReason;
}
