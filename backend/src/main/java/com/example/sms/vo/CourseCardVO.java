// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的高精度小数 BigDecimal（用于学分等精确数值）。
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程卡片 VO（学生选课中心 / 通用课程展示）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 课程卡片数据对象：学生选课中心及通用课程列表中以卡片形式展示课程信息
public class CourseCardVO {

    // 课程 ID：数据库主键
    private Long id;
    // 课程编号：教务系统分配的唯一课程编码
    private String courseCode;
    // 课程名称：课程标题
    private String courseName;
    // 学分：课程学分值（BigDecimal 保证精度）
    private BigDecimal credit;
    // 总学时：课程教学总课时数
    private Integer hours;
    // 课程封面图 URL：卡片上展示的图片地址
    private String coverImageUrl;
    // 授课教师姓名：冗余展示教师姓名，便于卡片直接显示
    private String teacherName;
    // 上课时间：文字描述排课时间
    private String schedule;
    // 上课地点：文字描述上课教室/地点
    private String location;
    // 选课容量上限：最多可选课人数
    private Integer capacity;
    // 当前已选人数：已选课学生数
    private Integer currentEnrolled;
    /** 课程状态：UNPUBLISHED 未发布 | PUBLISHED 已发布 */
    // 课程状态：UNPUBLISHED（未发布）/ PUBLISHED（已发布）
    private String status;
    /** 当前学生是否已选 */
    // 当前学生是否已选：针对当前登录学生标记是否已选该课程（用于前端按钮状态）
    private Boolean enrolled;
    /** 是否满员 */
    // 是否满员：当前已选人数是否达到容量上限（用于前端禁用选课按钮）
    private Boolean full;
}
