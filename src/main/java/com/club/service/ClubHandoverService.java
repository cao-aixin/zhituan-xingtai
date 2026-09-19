package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.dto.HandoverDTO;
import com.club.dto.HandoverVO;
import com.club.entity.ClubHandover;

import java.util.List;

/**
 * 社团换届服务
 */
public interface ClubHandoverService extends IService<ClubHandover> {

    /**
     * 发起换届：校验负责人权限 -> 变更 club.leader_id -> 维护成员表会长 ->
     * 新负责人无负责人角色时授予 CLUB_LEADER -> 写换届记录 -> 站内消息通知新旧负责人
     */
    void handover(HandoverDTO dto);

    /**
     * 换届记录列表（数据权限：管理员全部；社团负责人仅本社团；学生无权查看）
     */
    List<HandoverVO> list(Long clubId);
}
