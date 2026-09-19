package com.club.config;

import cn.dev33.satoken.stp.StpInterface;
import com.club.service.UserService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源：把 sys_role 角色编码接入 Sa-Token
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final UserService userService;

    public StpInterfaceImpl(UserService userService) {
        this.userService = userService;
    }

    /** 权限列表（本项目以角色判断为主，权限码返回角色编码） */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return getRoleList(loginId, loginType);
    }

    /** 角色列表 */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userService.getRoleCodes(Long.valueOf(loginId.toString()));
    }
}
