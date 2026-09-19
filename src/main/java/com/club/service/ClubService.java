package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.dto.ClubDTO;
import com.club.entity.Club;

import java.util.List;

/** 社团服务 */
public interface ClubService extends IService<Club> {

    /** 社团列表（学生浏览：仅正常状态；管理员：全部） */
    List<Club> listClubs(String keyword);

    /** 学生申请创建社团（状态=待审核，申请人成为负责人） */
    void apply(ClubDTO dto);

    /** 待审核社团列表（管理员） */
    List<Club> listPending();

    /** 社团申请审核（管理员：通过/驳回，通过后申请人授予负责人角色） */
    void audit(ClubDTO dto);

    /** 我管理的社团（负责人） */
    List<Club> myManagedClubs();

    /** 社团详情 */
    Club detail(Long id);
}
