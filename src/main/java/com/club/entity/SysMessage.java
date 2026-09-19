package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息表（区分待办任务 / 普通通知，带未读标记）
 */
@Data
@TableName("sys_message")
public class SysMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 接收人用户ID */
    private Long userId;
    /** 消息标题 */
    private String title;
    /** 消息内容 */
    private String content;
    /** 类型：0待办任务 1普通通知（见 MessageType 枚举） */
    private Integer msgType;
    /** 是否已读：0未读 1已读 */
    private Integer isRead;
    /** 关联业务ID（如活动ID，便于前端跳转处理） */
    private Long bizId;
    private LocalDateTime createTime;
}
