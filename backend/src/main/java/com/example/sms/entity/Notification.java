package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知主表实体
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL */
    private String type;

    private String title;

    private String content;

    /** ADMIN | TEACHER | SYSTEM */
    private String senderType;

    private Long senderId;

    private String senderName;

    private LocalDateTime createdAt;
}
