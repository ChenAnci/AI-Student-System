// 包声明：本类位于 entity（实体）包，存放与数据库表一一映射的实体类
package com.example.sms.entity;

// ---------- import 区域说明 ----------
// 此处导入 MyBatis-Plus 的注解（实体类与数据库表、主键的映射）、
// Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 LocalDateTime（本地日期时间）类型。
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知主表实体，对应数据库表 notification：一条通知一条记录，接收人展开在通知接收明细表
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("notification")：MyBatis-Plus 注解，声明该实体映射数据库表 notification（站内通知主表）
@TableName("notification")
public class Notification {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL */
    // 通知类型枚举：MANUAL（管理员手动发送）/ GRADE_PUBLISH（成绩发布提醒）/
    // COURSE_CHANGE（课程变动提醒）/ ENROLL（选课结果提醒）
    private String type;

    // 标题/正文冗余存储于主表：每条接收明细不再复制内容，查询收件箱时按 notification_id 关联主表取内容
    // 通知标题：冗余存储于主表，接收明细表不重复保存标题，收件箱查询时按通知 ID 关联主表获取
    private String title;

    /** 通知正文内容 */
    // 通知正文：通知的详细文字内容
    private String content;

    /** ADMIN | TEACHER | SYSTEM */
    // 发送者类型枚举：ADMIN（管理员）/ TEACHER（教师）/ SYSTEM（系统自动发送）
    private String senderType;

    // 发送者 id：教师/管理员记录其用户 id；系统通知为 null（发送者是"系统"而非某个用户）
    // 发送者 ID：教师/管理员通知记录其用户 id；系统通知该字段为 null（发送者是"系统"而非具体用户）
    private Long senderId;

    // 发送者姓名冗余：发件箱列表直接展示，避免每次再查用户表
    // 发送者姓名：冗余存储，发件箱列表可直接展示，无需再查用户表获取
    private String senderName;

    // 发送时间：由数据库默认值/MyBatis-Plus 填充，WS 推送与列表展示均依赖它
    // 发送时间：由数据库默认值（NOW()）填充，WebSocket 推送与列表展示均依赖此时间
    private LocalDateTime createdAt;
}
