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

    /** 通知类型：MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL（供前端按类型展示图标/标签） */
    private String type;

    private String title;

    private String content;

    private String senderName;

    /** 当前学生是否已读（发件箱为 null） */
    private Boolean read;

    /** 发送时间（接收明细表另有 readAt 记录已读时间，此处不展示） */
    private LocalDateTime createdAt;
}
