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
 * OAuth 登录绑定关系，对应数据库表 oauth_binding（GitHub 账号 ↔ 系统工号/学号）
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("oauth_binding")：MyBatis-Plus 注解，声明该实体映射数据库表 oauth_binding（OAuth 账号绑定关系表）
@TableName("oauth_binding")
public class OAuthBinding {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统账号（工号/学号，对应 staff.staff_no / student.student_no） */
    // 系统账号：工号或学号，对应 staff.staff_no 或 student.student_no，用于绑定后免密登录系统
    private String userNo;

    /** OAuth 提供方：github */
    // OAuth 提供方：目前仅支持 github，标识第三方账号来源
    private String provider;

    /** GitHub 用户唯一 id */
    // GitHub 用户唯一 ID：GitHub 侧的用户标识，用于识别并建立账号映射关系
    private String providerUid;

    /** 绑定创建时间 */
    // 绑定创建时间：该 OAuth 绑定关系建立的时间
    private LocalDateTime createdAt;

    /** 绑定更新时间 */
    // 绑定更新时间：绑定信息最近变更的时间
    private LocalDateTime updatedAt;
}
