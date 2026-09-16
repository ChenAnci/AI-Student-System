package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    /** 0未读 1已读 */
    @TableField("is_read")
    private Boolean read;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
