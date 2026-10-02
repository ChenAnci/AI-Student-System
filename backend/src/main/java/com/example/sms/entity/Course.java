// 包声明：本类位于 entity（实体）包，存放与数据库表一一映射的实体类
package com.example.sms.entity;

// ---------- import 区域说明 ----------
// 此处导入 MyBatis-Plus 的注解（实体类与数据库表、主键的映射）、
// Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的高精度小数 BigDecimal 和日期时间 LocalDateTime 类型。
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程实体，对应数据库表 course：课程基本信息、容量与发布状态
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("course")：MyBatis-Plus 注解，声明该实体映射数据库中的 course 表
@TableName("course")
public class Course {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编号 */
    // 课程编号：教务系统分配的唯一课程编码（如 CS101），用于对外展示与精确检索
    private String courseCode;

    /** 课程名称 */
    // 课程名称：选课列表/课程卡片上展示的课程标题
    private String courseName;

    /** 学分 */
    // 学分：BigDecimal 保证精度，用于学生已修总学分的累加统计（如 3.0 分）
    private BigDecimal credit;

    /** 总学时 */
    // 总学时：课程教学总课时数（整数，单位：学时）
    private Integer hours;

    /** 课程封面图URL */
    // 课程封面图 URL：选课中心课程卡片展示的图片地址
    private String coverImageUrl;

    /** 授课教师ID */
    // 授课教师 ID：外键，关联 staff 表的 id，确定该课程由哪位教师授课
    private Long teacherId;

    /** 上课时间 */
    // 上课时间：文字描述排课时间，如 "周一 3-4 节"，不对时间做结构化解析
    private String schedule;

    /** 上课地点 */
    // 上课地点：文字描述上课教室/地点，如 "A101 机房"
    private String location;

    /** 选课容量上限 */
    // 选课容量上限：该课程最多允许选课的学生人数，用于选课时的名额校验
    private Integer capacity;

    /** 当前已选人数 */
    // 当前已选人数：已成功选课的学生数，选课时若达到 capacity 则无法再选
    private Integer currentEnrolled;

    /** UNPUBLISHED | PUBLISHED */
    // 发布状态枚举：UNPUBLISHED（未发布，学生不可见）/ PUBLISHED（已发布，学生可选课）
    private String status;

    /** 更新时间（选课中心按此倒序展示课程） */
    // 更新时间：课程信息最近变更时间，选课中心按该字段倒序（最新在前）展示课程
    private LocalDateTime updatedAt;
}
