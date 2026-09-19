package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.entity.ClubMember;

import java.util.List;
import java.util.Map;

/** 社团成员服务 */
public interface ClubMemberService extends IService<ClubMember> {

    /** 学生申请入社 */
    void join(Long clubId);

    /** 退出社团 */
    void quit(Long clubId);

    /** 成员入社审核（负责人：通过/驳回） */
    void auditMember(Long memberId, boolean approved);

    /** 直接写入负责人成员记录（社团创建成功时调用） */
    void addLeaderMember(Long clubId, Long userId, String memberRole);

    /** 社团成员列表（负责人，含用户信息） */
    List<Map<String, Object>> listMembers(Long clubId);

    /** 查询用户在指定社团的负责人记录（数据权限用） */
    ClubMember getLeaderMember(Long clubId, Long userId);

    /** 查询用户管理的社团ID（一个用户最多负责一个社团，简化约束） */
    Long getManagedClubId(Long userId);

    /** 用户是否为指定社团的正常成员 */
    boolean isNormalMember(Long clubId, Long userId);

    /** 用户所在的所有正常社团ID */
    List<Long> myClubIds(Long userId);
}
