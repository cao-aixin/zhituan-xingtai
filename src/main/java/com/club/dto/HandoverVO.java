package com.club.dto;

import lombok.Data;

/**
 * 换届记录出参（含社团名/新旧负责人姓名展示字段）
 */
@Data
public class HandoverVO {

    private Long id;
    private Long clubId;
    /** 社团名称 */
    private String clubName;
    private Long oldLeaderId;
    /** 原负责人姓名 */
    private String oldLeaderName;
    private Long newLeaderId;
    /** 新负责人姓名 */
    private String newLeaderName;
    /** 交接说明 */
    private String note;
    /** 换届时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;
}
