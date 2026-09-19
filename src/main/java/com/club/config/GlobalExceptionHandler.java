package com.club.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.club.util.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：所有异常统一转 Result，AI/业务报错不把堆栈抛到前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public org.springframework.http.ResponseEntity<Object> handleBiz(BizException e) {
        return org.springframework.http.ResponseEntity.ok()
                .body(java.util.Map.of("code", e.getCode(), "msg", e.getMessage(), "data", ""));
    }

    /** 未登录 / token 无效 */
    @ExceptionHandler(NotLoginException.class)
    public Object handleNotLogin(NotLoginException e) {
        return java.util.Map.of("code", 401, "msg", "未登录或登录已失效", "data", "");
    }

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数不合法");
        return java.util.Map.of("code", 400, "msg", msg, "data", "");
    }

    /** 上传文件超过大小限制（>10MB）：返回 400 而非 500 */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return java.util.Map.of("code", 400, "msg", "上传文件过大，单个文件不能超过 10MB", "data", "");
    }

    /** 兜底异常：记录日志，返回友好提示 */
    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e) {
        log.error("系统异常", e);
        return java.util.Map.of("code", 500, "msg", "系统繁忙，请稍后重试", "data", "");
    }
}
