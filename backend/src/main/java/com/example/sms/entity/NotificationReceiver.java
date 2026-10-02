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
 * 通知接收明细实体，对应数据库表 notification_receiver（一对多：一条通知对应多个学生）
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("notification_receiver")：MyBatis-Plus 注解，声明该实体映射数据库表 notification_receiver（通知接收明细表）
@TableName("notification_receiver")
public class NotificationReceiver {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属通知（主表 id）：一条通知对多个学生展开成多条明细，均指向同一 notificationId
    // 通知 ID：外键，关联 notification 主表 id；一条通知发给 N 个学生就产生 N 条明细，它们共享同一通知 ID
    private Long notificationId;

    // 接收学生 id：收件箱查询/未读数/已读标记都以此字段为属主边界
    // 接收学生 ID：外键，关联 student 表 id；收件箱查询、未读数统计、已读标记均以该字段为准
    private Long studentId;

    /** 0未读 1已读（字段名避免 MySQL 保留字 read） */
    // 已读标记：false/0 未读，true/1 已读；字段名取 isRead 是为了避开 MySQL 保留字 read
    private Boolean isRead;

    // 已读时间：标记已读时写入，可支撑"已读回执"类统计
    // 已读时间：学生标记已读时写入当前时间，可用于"已读回执"类统计
    private LocalDateTime readAt;

    /** 创建时间（接收时间），批量插入时由数据库 NOW() 填充 */
    // 创建时间（即接收时间）：批量插入接收明细时由数据库 NOW() 函数统一填充
    private LocalDateTime createdAt;
}
