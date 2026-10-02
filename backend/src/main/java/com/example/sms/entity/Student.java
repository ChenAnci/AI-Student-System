// 包声明：本类位于 entity（实体）包，存放与数据库表一一映射的实体类
package com.example.sms.entity;

// ---------- import 区域说明 ----------
// 此处导入 MyBatis-Plus 的注解（实体类与数据库表、主键的映射）、
// Jackson 的 @JsonProperty（JSON 序列化控制）、Lombok 的 @Data，
// 以及 JDK 的 BigDecimal（高精度小数）和 LocalDateTime（本地日期时间）类型。
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生实体，对应数据库表 student：学生账号、学籍信息与学业数据（学分/绩点）
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("student")：MyBatis-Plus 注解，声明该实体映射数据库表 student（学生表）
@TableName("student")
public class Student {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号（登录账号） */
    // 学号：学生登录系统的账号，全局唯一
    private String studentNo;

    /** bcrypt 加密密码，序列化时忽略 */
    // @JsonProperty(access = WRITE_ONLY)：只允许反序列化（接收前端传入），
    // 序列化输出给前端时忽略该字段，防止密码哈希泄露
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    // 密码哈希：使用 bcrypt 加密后的密码串，仅用于登录校验，不对外输出
    private String passwordHash;

    /** 姓名 */
    // 姓名：学生真实姓名
    private String realName;

    /** ENABLED | FROZEN | SUSPENDED */
    // 账号状态枚举：ENABLED（正常）/ FROZEN（冻结）/ SUSPENDED（休学，暂停使用）
    private String status;

    /** 令牌版本号：改密/禁用/改角色时 +1，用于吊销旧 token */
    // 令牌版本号：修改密码/禁用账号时自增 1，用于使已签发的旧 token 失效
    private Integer tokenVersion;

    /** 男/女 */
    // 性别：男 / 女
    private String gender;

    /** 手机号 */
    // 手机号：联系方式，便于系统发送通知
    private String phone;

    /** 院系 */
    // 院系：学生所属院系名称
    private String department;

    /** 专业 */
    // 专业：学生所学专业名称
    private String major;

    /** 班级 */
    // 班级：学生所在班级名称
    private String className;

    /** 入学年份 */
    // 入学年份：学生入学的年份（整数，如 2023），用于学籍统计
    private Integer enrollmentYear;

    /** 已修总学分 */
    // 已修总学分：学生目前已获得并通过考核的学分累计，BigDecimal 保证精度
    private BigDecimal totalEarnedCredits;

    /** 毕业要求总学分 */
    // 毕业要求总学分：学生毕业需达到的总学分要求，用于判断是否满足毕业条件
    private BigDecimal requiredCredits;

    /** 累计平均绩点 */
    // 累计平均绩点 GPA：衡量学业水平的综合指标，通常取值 0.0 ~ 4.0（或 5.0）
    private BigDecimal gpa;

    /** 创建时间 */
    // 创建时间：学生账号创建的时刻
    private LocalDateTime createdAt;

    /** 更新时间 */
    // 更新时间：学生信息最近变更的时刻
    private LocalDateTime updatedAt;
}
