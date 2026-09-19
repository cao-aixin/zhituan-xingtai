package com.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起换届入参
 */
@Data
public class HandoverDTO {

    /** 社团ID */
    @NotNull(message = "社团ID不能为空")
    private Long clubId;

    /** 新负责人学号 */
    @NotBlank(message = "新负责人学号不能为空")
    private String newLeaderStudentNo;

    /** 交接说明 */
    private String note;
}
