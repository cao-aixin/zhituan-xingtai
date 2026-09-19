package com.club.util;

import lombok.Getter;

/**
 * 业务异常（全局异常处理器统一捕获，返回友好提示，不抛堆栈到前端）
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        this(400, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
