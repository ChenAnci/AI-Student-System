// 包声明：本类位于 entity（实体）包，存放与数据库表一一映射的实体类
package com.example.sms.entity;

// ---------- import 区域说明 ----------
// 此处导入 MyBatis-Plus 的注解（实体类与数据库表、主键的映射）、
// Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 BigDecimal（高精度小数）和 LocalDateTime（本地日期时间）类型。
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生选课实体，对应数据库表 student_course：选课关系、成绩与考试标记
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("student_course")：MyBatis-Plus 注解，声明该实体映射数据库表 student_course（学生选课关联表）
@TableName("student_course")
public class StudentCourse {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID（关联 student.id） */
    // 学生 ID：外键，关联 student 表主键，标明是哪位学生选的课
    private Long studentId;

    /** 课程ID（关联 course.id） */
    // 课程 ID：外键，关联 course 表主键，标明选的是哪门课程
    private Long courseId;

    /** 总评成绩（NULL 表示未录入） */
    // 总评成绩：课程最终成绩（0~100 分制）；NULL 表示教师尚未录入成绩
    private BigDecimal score;

    /** NORMAL正常 | DEFER缓考 | ABSENT缺考 | CHEAT舞弊 */
    // 考试标记枚举：NORMAL（正常参加考试）/ DEFER（缓考）/ ABSENT（缺考）/ CHEAT（考试舞弊）
    private String mark;

    /** 选课时间 */
    // 选课时间：学生完成选课时创建记录的时刻
    private LocalDateTime createdAt;

    /** 更新时间（成绩/标记变更时刷新） */
    // 更新时间：成绩或考试标记每次变更时刷新的时刻
    private LocalDateTime updatedAt;
}
