package com.club.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.dto.LoginDTO;
import com.club.dto.UserDTO;
import com.club.dto.UserVO;
import com.club.entity.Club;
import com.club.entity.SysRole;
import com.club.entity.SysUser;
import com.club.entity.SysUserRole;
import com.club.mapper.SysRoleMapper;
import com.club.mapper.SysUserMapper;
import com.club.mapper.SysUserRoleMapper;
import com.club.service.ClubMemberService;
import com.club.service.UserService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final ClubMemberService clubMemberService;
    private final AuthUtil authUtil;

    public UserServiceImpl(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                           ClubMemberService clubMemberService, AuthUtil authUtil) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.clubMemberService = clubMemberService;
        this.authUtil = authUtil;
    }

    @Override
    public UserVO login(LoginDTO dto) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentNo, dto.getStudentNo()));
        if (user == null) {
            throw new BizException("账号不存在");
        }
        String md5 = DigestUtils.md5DigestAsHex(dto.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!md5.equals(user.getPassword())) {
            throw new BizException("密码错误");
        }
        // Sa-Token 登录，返回 token
        StpUtil.login(user.getId());
        UserVO vo = toVO(user);
        vo.setToken(StpUtil.getTokenValue());
        return vo;
    }

    @Override
    public UserVO currentUser() {
        SysUser user = getById(authUtil.currentUserId());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(ur -> roleMapper.selectById(ur.getRoleId()))
                .filter(r -> r != null)
                .map(SysRole::getRoleCode)
                .toList();
    }

    @Override
    public List<UserVO> listUsers(String keyword) {
        // 功能权限：仅管理员可查用户列表
        authUtil.checkAdmin();
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(SysUser::getName, keyword).or().like(SysUser::getStudentNo, keyword);
        }
        return list(qw).stream().map(this::toVO).toList();
    }

    @Override
    public void addUser(UserDTO dto) {
        authUtil.checkAdmin();
        if (getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStudentNo, dto.getStudentNo())) != null) {
            throw new BizException("学号已存在");
        }
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        // 新用户默认密码 123456
        user.setPassword(DigestUtils.md5DigestAsHex("123456".getBytes(StandardCharsets.UTF_8)));
        user.setAiRecommend(1);
        save(user);
        if (dto.getRoleId() != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(dto.getRoleId());
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> listAdminIds() {
        // 查询拥有 ADMIN 角色的所有用户ID
        SysRole adminRole = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, "ADMIN")).stream().findFirst().orElse(null);
        if (adminRole == null) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, adminRole.getId())).stream()
                .map(SysUserRole::getUserId).distinct().toList();
    }

    /** 实体转VO：附加角色、管理的社团 */
    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        List<String> roleCodes = getRoleCodes(user.getId());
        vo.setRoleCodes(roleCodes);
        vo.setRoleNames(roleCodes.stream()
                .map(code -> roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, code)).stream().findFirst()
                        .map(SysRole::getRoleName).orElse(code))
                .toList());
        vo.setManagedClubId(clubMemberService.getManagedClubId(user.getId()));
        return vo;
    }
}
