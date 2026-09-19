package com.club.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 活动报名/签到入参 */
@Data
public class SignupDTO {

    /** 活动ID */
    private Long activityId;
    /** 签到码 */
    private String checkinCode;
    /** 签到时间（出参展示） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime checkinTime;
}
