package com.club.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA（单页应用）转发控制器
 * Vue Router 使用 history 模式，刷新非根路径（如 /admin/clubAudit）时
 * 后端并无对应资源，需将其转发到 index.html 交给前端路由处理。
 * 仅转发非 /api、非 /file 的 GET 请求，不影响接口与静态资源。
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/login",
            "/messages",
            "/admin/**",
            "/clubManager/**",
            "/student/**"
    })
    public String forward() {
        // 统一转发到前端入口 index.html
        return "forward:/index.html";
    }
}
