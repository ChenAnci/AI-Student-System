package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知接收明细实体，对应数据库表 notification_receiver（一对多：一条通知对应多个学生）
 */
@Data
@TableName("notification_receiver")
public class NotificationReceiver {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属通知（主表 id）：一条通知对多个学生展开成多条明细，均指向同一 notificationId
    private Long notificationId;

    // 接收学生 id：收件箱查询/未读数/已读标记都以此字段为属主边界
    private Long studentId;

    /** 0未读 1已读（字段名避免 MySQL 保留字 read） */
    private Boolean isRead;

    // 已读时间：标记已读时写入，可支撑"已读回执"类统计
    private LocalDateTime readAt;

    /** 创建时间（接收时间），批量插入时由数据库 NOW() 填充 */
    private LocalDateTime createdAt;
}
