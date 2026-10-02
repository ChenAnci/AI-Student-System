// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 LocalDateTime（本地日期时间）类型。
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 站内通知的展示对象：学生收件箱与发件箱（管理员/教师视角）共用，
// 收件箱相关字段与发件箱相关字段通过注释区分
public class NotificationVO {

    /** 通知主表 id */
    // 通知主表 ID：对应 notification 表主键
    private Long id;

    /** 接收明细 id（学生用于标记已读；发件箱为 null） */
    // 接收明细 ID：对应 notification_receiver 表主键；学生收件箱用它标记已读，发件箱视角下为 null
    private Long receiverId;

    /** 通知类型：MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL（供前端按类型展示图标/标签） */
    // 通知类型：MANUAL（手动发送）/ GRADE_PUBLISH（成绩发布）/ COURSE_CHANGE（课程变动）/
    // ENROLL（选课结果）；前端按类型展示不同的图标/标签
    private String type;

    // 通知标题：通知的标题文字
    private String title;

    // 通知正文：通知的详细文字内容
    private String content;

    // 发送者姓名：冗余展示，无需再查用户表
    private String senderName;

    /** 当前学生是否已读（发件箱为 null） */
    // 当前学生是否已读：true 已读 / false 未读；发件箱视角下为 null
    private Boolean read;

    /** 发送时间（接收明细表另有 readAt 记录已读时间，此处不展示） */
    // 发送时间：通知创建（发送）的时间；已读时间记录在接收明细表的 readAt 字段，此处不展示
    private LocalDateTime createdAt;
}
