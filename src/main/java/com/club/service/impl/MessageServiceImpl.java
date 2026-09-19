package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.dto.MessageVO;
import com.club.entity.SysMessage;
import com.club.enums.MessageType;
import com.club.mapper.SysMessageMapper;
import com.club.service.MessageService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内消息服务实现：区分待办任务/普通通知，支持未读计数与已读
 */
@Service
public class MessageServiceImpl extends ServiceImpl<SysMessageMapper, SysMessage> implements MessageService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuthUtil authUtil;

    public MessageServiceImpl(AuthUtil authUtil) {
        this.authUtil = authUtil;
    }

    @Override
    public void send(List<Long> userIds, String title, String content, Integer msgType, Long bizId) {
        for (Long userId : userIds) {
            SysMessage msg = new SysMessage();
            msg.setUserId(userId);
            msg.setTitle(title);
            msg.setContent(content);
            msg.setMsgType(msgType);
            msg.setIsRead(0);
            msg.setBizId(bizId);
            msg.setCreateTime(LocalDateTime.now());
            save(msg);
        }
    }

    @Override
    public List<MessageVO> listMine(Integer msgType) {
        Long userId = authUtil.currentUserId();
        LambdaQueryWrapper<SysMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(SysMessage::getUserId, userId);
        if (msgType != null) {
            qw.eq(SysMessage::getMsgType, msgType);
        }
        qw.orderByDesc(SysMessage::getCreateTime);
        return list(qw).stream().map(this::toVO).toList();
    }

    @Override
    public Map<String, Object> unreadCount(Integer msgType) {
        Long userId = authUtil.currentUserId();
        LambdaQueryWrapper<SysMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getIsRead, 0);
        if (msgType != null) {
            qw.eq(SysMessage::getMsgType, msgType);
        }
        long count = count(qw);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unreadCount", count);
        return data;
    }

    @Override
    public void markRead(Long messageId) {
        Long userId = authUtil.currentUserId();
        SysMessage msg = getById(messageId);
        // 数据权限：只能读自己的消息
        if (msg == null || !msg.getUserId().equals(userId)) {
            throw new BizException("消息不存在");
        }
        msg.setIsRead(1);
        updateById(msg);
    }

    @Override
    public void markAllRead() {
        Long userId = authUtil.currentUserId();
        lambdaUpdate()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getIsRead, 0)
                .set(SysMessage::getIsRead, 1)
                .update();
    }

    private MessageVO toVO(SysMessage msg) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setTitle(msg.getTitle());
        vo.setContent(msg.getContent());
        vo.setMsgType(msg.getMsgType());
        vo.setMsgTypeDesc(MessageType.TODO.getCode().equals(msg.getMsgType())
                ? MessageType.TODO.getDesc() : MessageType.NOTICE.getDesc());
        vo.setIsRead(msg.getIsRead());
        vo.setBizId(msg.getBizId());
        vo.setCreateTime(msg.getCreateTime() == null ? null : msg.getCreateTime().format(FMT));
        return vo;
    }
}
