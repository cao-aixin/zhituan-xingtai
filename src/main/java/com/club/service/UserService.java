package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.dto.LoginDTO;
import com.club.dto.UserDTO;
import com.club.dto.UserVO;
import com.club.entity.SysUser;

import java.util.List;

/** 用户服务 */
public interface UserService extends IService<SysUser> {

    /** 登录：校验账号密码，返回 token 与用户信息 */
    UserVO login(LoginDTO dto);

    /** 获取当前登录用户信息（含角色、管理的社团ID） */
    UserVO currentUser();

    /** 查询用户角色编码列表 */
    List<String> getRoleCodes(Long userId);

    /** 用户列表（管理员，按关键字搜索） */
    List<UserVO> listUsers(String keyword);

    /** 管理员新增用户（默认密码123456，指定角色） */
    void addUser(UserDTO dto);

    /** 查询所有学校管理员用户ID（推送审批待办用） */
    List<Long> listAdminIds();
}
