// 包声明：本类位于 entity（实体）包，存放与数据库表一一映射的实体类
package com.example.sms.entity;

// ---------- import 区域说明 ----------
// 此处导入 MyBatis-Plus 的注解（实体类与数据库表、主键的映射）、
// Jackson 的 @JsonProperty（JSON 序列化控制）、Lombok 的 @Data，
// 以及 JDK 的 LocalDateTime（本地日期时间）类型。
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教职工实体，对应数据库表 staff：教职工账号（工号登录）、角色（管理员/教师）与所属院系
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("staff")：MyBatis-Plus 注解，声明该实体映射数据库表 staff（教职工表）
@TableName("staff")
public class Staff {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号（登录账号） */
    // 工号：教职工登录系统的账号，全局唯一
    private String staffNo;

    /** bcrypt 加密密码，序列化时忽略 */
    // @JsonProperty(access = WRITE_ONLY)：只允许反序列化（接收前端传入），
    // 序列化输出给前端时忽略该字段，防止密码哈希泄露
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    // 密码哈希：使用 bcrypt 加密后的密码串，仅用于登录校验，不对外输出
    private String passwordHash;

    /** 姓名 */
    // 姓名：教职工真实姓名
    private String realName;

    /** ADMIN | TEACHER */
    // 角色类型枚举：ADMIN（管理员）/ TEACHER（教师），决定系统权限范围
    private String roleType;

    /** ENABLED | FROZEN */
    // 账号状态枚举：ENABLED（正常可用）/ FROZEN（已冻结，禁止登录）
    private String status;

    /** 令牌版本号：改密/禁用/改角色时 +1，用于吊销旧 token */
    // 令牌版本号：修改密码/禁用账号/变更角色时自增 1，用于使已签发的旧 token 失效
    private Integer tokenVersion;

    /** 所属院系 */
    // 所属院系：教职工所在的院系名称
    private String department;

    /** 手机号 */
    // 手机号：联系方式，便于系统发送通知
    private String phone;

    /** 创建时间 */
    // 创建时间：账号创建的时刻
    private LocalDateTime createdAt;

    /** 更新时间 */
    // 更新时间：账号信息最近变更的时刻
    private LocalDateTime updatedAt;
}
