// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：MyBatis-Plus 分页对象 Page、统一响应体 Result、通知 DTO/VO、通知服务接口、Swagger 注解、Spring MVC 注解与参数校验 =====
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
// Swagger 注解：把本控制器在文档中归入"站内通知"分组
@Api(tags = "站内通知")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/notifications 开头
@RequestMapping("/api/notifications")
// 站内通知控制器：负责学生收件箱/未读数/已读操作与老师教秘发件箱/发送通知等请求，业务逻辑委托给 NotificationService
public class NotificationController {

    // 自动注入通知服务：由 Spring 容器装配 NotificationService 的实现类
    @Autowired
    private NotificationService notificationService;

    // Swagger 接口说明：学生：收件箱分页
    @ApiOperation("学生：收件箱分页")
    // GET 映射：对 /api/notifications 发起 GET 表示分页查询收件箱（默认第一页）
    @GetMapping
    // 学生收件箱分页：page 为页码（默认 1），size 为每页条数（默认 10）
    public Result<Page<NotificationVO>> inbox(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 学生收件箱：分页拉取自己收到的通知（含已读/未读状态），供通知列表页展示
        // 调用服务层分页查询当前学生的收件箱
        return Result.success(notificationService.inbox(page, size));
    }

    // Swagger 接口说明：学生：未读数
    @ApiOperation("学生：未读数")
    // GET 映射：访问 /api/notifications/unread-count 查询未读通知数量
    @GetMapping("/unread-count")
    // 未读数接口：无需参数，返回当前学生未读通知的数量
    public Result<Long> unreadCount() {
        // 未读角标：导航栏/首页红点数，供前端轮询或登录后拉取
        // 调用服务层统计当前学生的未读通知数
        return Result.success(notificationService.unreadCount());
    }

    // Swagger 接口说明：学生：标记已读
    @ApiOperation("学生：标记已读")
    // PUT 映射：路径模板 /{receiverId}/read，将指定接收明细标记为已读
    @PutMapping("/{receiverId}/read")
    // 标记已读：@PathVariable 绑定接收明细 id（receiverId）
    public Result<Void> markRead(@PathVariable Long receiverId) {
        // 标记单条通知已读：receiverId 为接收明细 id（收件箱列表/WS 推送消息中携带）
        // 调用服务层将该条通知标记为已读
        notificationService.markRead(receiverId);
        // 操作成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：学生：全部已读
    @ApiOperation("学生：全部已读")
    // PUT 映射：访问 /api/notifications/read-all 一键全部已读
    @PutMapping("/read-all")
    // 全部已读接口：无需参数，作用于当前学生
    public Result<Void> readAll() {
        // 一键全部已读：把当前学生的全部未读通知批量置为已读（"清空红点"）
        // 调用服务层批量置为已读
        notificationService.readAll();
        // 操作成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：老师/教秘：发件箱分页
    @ApiOperation("老师/教秘：发件箱分页")
    // GET 映射：访问 /api/notifications/sent 分页查询发件箱
    @GetMapping("/sent")
    // 发件箱分页：page 为页码（默认 1），size 为每页条数（默认 10）
    public Result<Page<NotificationVO>> sent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 发件箱：老师/教秘分页查看自己发送过的通知记录
        // 调用服务层分页查询当前用户发送过的通知
        return Result.success(notificationService.sent(page, size));
    }

    // Swagger 接口说明：老师/教秘：发送通知
    @ApiOperation("老师/教秘：发送通知")
    // POST 映射：访问 /api/notifications/send 发送通知
    @PostMapping("/send")
    // 发送通知：@Valid 触发 DTO 非空等校验，@RequestBody 绑定 SendNotificationDTO
    public Result<Void> send(@Valid @RequestBody SendNotificationDTO dto) {
        // 发送通知：@Valid 触发 DTO 上的非空校验；具体接收人解析与权限校验在 service 层完成
        // 调用服务层发送通知
        notificationService.send(dto);
        // 发送成功返回成功响应
        return Result.success();
    }
}
