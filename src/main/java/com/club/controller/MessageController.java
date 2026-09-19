package com.club.controller;

import com.club.dto.MessageVO;
import com.club.service.MessageService;
import com.club.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内消息接口：待办任务/普通通知、未读计数、已读
 */
@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** 我的消息列表（msgType：0待办任务 1普通通知，不传查全部） */
    @GetMapping("/list")
    public Result<List<MessageVO>> list(@RequestParam(required = false) Integer msgType) {
        return Result.ok(messageService.listMine(msgType));
    }

    /** 未读消息数（msgType可选，返回 {unreadCount: n}） */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(@RequestParam(required = false) Integer msgType) {
        return Result.ok(messageService.unreadCount(msgType));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        messageService.markAllRead();
        return Result.ok();
    }
}
