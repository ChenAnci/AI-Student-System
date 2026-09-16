package com.example.sms.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象
 */
@Data
public class NotificationVO {

    /** 通知主表 id */
    private Long id;

    /** 接收明细 id（学生用于标记已读；发件箱为 null） */
    private Long receiverId;

    private String type;

    private String title;

    private String content;

    private String senderName;

    /** 当前学生是否已读（发件箱为 null） */
    private Boolean read;

    private LocalDateTime createdAt;
}
