package com.example.sms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.sms.common.Result;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.service.NotificationService;
import com.example.sms.vo.NotificationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 站内通知接口
 */
@Api(tags = "站内通知")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @ApiOperation("学生：收件箱分页")
    @GetMapping
    public Result<Page<NotificationVO>> inbox(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 学生收件箱：分页拉取自己收到的通知（含已读/未读状态），供通知列表页展示
        return Result.success(notificationService.inbox(page, size));
    }

    @ApiOperation("学生：未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        // 未读角标：导航栏/首页红点数，供前端轮询或登录后拉取
        return Result.success(notificationService.unreadCount());
    }

    @ApiOperation("学生：标记已读")
    @PutMapping("/{receiverId}/read")
    public Result<Void> markRead(@PathVariable Long receiverId) {
        // 标记单条通知已读：receiverId 为接收明细 id（收件箱列表/WS 推送消息中携带）
        notificationService.markRead(receiverId);
        return Result.success();
    }

    @ApiOperation("学生：全部已读")
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        // 一键全部已读：把当前学生的全部未读通知批量置为已读（"清空红点"）
        notificationService.readAll();
        return Result.success();
    }

    @ApiOperation("老师/教秘：发件箱分页")
    @GetMapping("/sent")
    public Result<Page<NotificationVO>> sent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 发件箱：老师/教秘分页查看自己发送过的通知记录
        return Result.success(notificationService.sent(page, size));
    }

    @ApiOperation("老师/教秘：发送通知")
    @PostMapping("/send")
    public Result<Void> send(@Valid @RequestBody SendNotificationDTO dto) {
        // 发送通知：@Valid 触发 DTO 上的非空校验；具体接收人解析与权限校验在 service 层完成
        notificationService.send(dto);
        return Result.success();
    }
}
