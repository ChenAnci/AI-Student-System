package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知主表实体，对应数据库表 notification：一条通知一条记录，接收人展开在通知接收明细表
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL */
    private String type;

    // 标题/正文冗余存储于主表：每条接收明细不再复制内容，查询收件箱时按 notification_id 关联主表取内容
    private String title;

    /** 通知正文内容 */
    private String content;

    /** ADMIN | TEACHER | SYSTEM */
    private String senderType;

    // 发送者 id：教师/管理员记录其用户 id；系统通知为 null（发送者是"系统"而非某个用户）
    private Long senderId;

    // 发送者姓名冗余：发件箱列表直接展示，避免每次再查用户表
    private String senderName;

    // 发送时间：由数据库默认值/MyBatis-Plus 填充，WS 推送与列表展示均依赖它
    private LocalDateTime createdAt;
}
