package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.dto.MessageVO;
import com.club.entity.SysMessage;

import java.util.List;
import java.util.Map;

/** 站内消息服务 */
public interface MessageService extends IService<SysMessage> {

    /** 发送消息（可批量推送给多个用户） */
    void send(List<Long> userIds, String title, String content, Integer msgType, Long bizId);

    /** 我的消息列表（可按类型过滤：0待办 1通知） */
    List<MessageVO> listMine(Integer msgType);

    /** 未读消息数（可按类型过滤） */
    Map<String, Object> unreadCount(Integer msgType);

    /** 标记单条已读（仅本人消息） */
    void markRead(Long messageId);

    /** 全部已读 */
    void markAllRead();
}
