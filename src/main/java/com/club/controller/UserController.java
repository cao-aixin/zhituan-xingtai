package com.club.controller;

import com.club.dto.UserDTO;
import com.club.dto.UserVO;
import com.club.service.UserService;
import com.club.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口（用户列表/新增仅管理员）
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 用户列表（仅管理员，支持姓名/学号关键字） */
    @GetMapping("/list")
    public Result<List<UserVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(keyword));
    }

    /** 新增用户（仅管理员，默认密码123456） */
    @PostMapping
    public Result<Void> add(@RequestBody UserDTO dto) {
        userService.addUser(dto);
        return Result.ok();
    }

    /** 用户详情 */
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getById(id) == null ? null
                : toVO(id));
    }

    private UserVO toVO(Long id) {
        // 简化：详情复用列表逻辑（仅管理员可见完整列表，此处返回基础信息）
        return userService.listUsers(null).stream()
                .filter(u -> u.getId().equals(id)).findFirst().orElse(null);
    }
}
