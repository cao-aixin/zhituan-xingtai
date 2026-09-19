package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团成员中间表
 */
@Data
@TableName("club_member")
public class ClubMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clubId;
    private Long userId;
    /** 成员角色：会长 / 部长 / 会员 等 */
    private String memberRole;
    /** 状态：0待审核 1正常 2已退出 */
    private Integer status;
    private LocalDateTime joinTime;
    private LocalDateTime createTime;
}
