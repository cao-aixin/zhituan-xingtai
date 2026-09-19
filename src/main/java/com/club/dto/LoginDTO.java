package com.club.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录入参 */
@Data
public class LoginDTO {

    /** 学号 / admin 账号 */
    @NotBlank(message = "账号不能为空")
    private String studentNo;
    /** 密码（明文传输，后端 MD5 比对） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
