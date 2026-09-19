package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团换届记录表
 */
@Data
@TableName("club_handover")
public class ClubHandover {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 社团ID */
    private Long clubId;
    /** 原负责人用户ID */
    private Long oldLeaderId;
    /** 新负责人用户ID */
    private Long newLeaderId;
    /** 交接说明 */
    private String note;
    private LocalDateTime createTime;
}
