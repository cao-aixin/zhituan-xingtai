package com.club.dto;

import lombok.Data;

/** 消息出参 */
@Data
public class MessageVO {

    private Long id;
    private String title;
    private String content;
    /** 0待办任务 1普通通知 */
    private Integer msgType;
    private String msgTypeDesc;
    /** 0未读 1已读 */
    private Integer isRead;
    /** 关联业务ID（活动ID等） */
    private Long bizId;
    private String createTime;
}
