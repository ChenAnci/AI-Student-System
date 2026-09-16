package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知接收明细实体（一对多：一条通知对应多个学生）
 */
@Data
@TableName("notification_receiver")
public class NotificationReceiver {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long notificationId;

    private Long studentId;

    /** 0未读 1已读（字段名避免 MySQL 保留字 read） */
    private Boolean isRead;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
