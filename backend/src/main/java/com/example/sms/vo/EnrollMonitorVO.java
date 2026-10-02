// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的高精度小数 BigDecimal（用于精确数值计算）。
import lombok.Data;

import java.math.BigDecimal;

/**
 * 选课监控 VO（教学秘书）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教学秘书（管理员）选课监控列表项：实时查看各课程选课人数与剩余名额
public class EnrollMonitorVO {

    // 课程 ID：数据库主键
    private Long courseId;
    // 课程编号：教务系统分配的唯一课程编码
    private String courseCode;
    // 课程名称：课程标题
    private String courseName;
    // 授课教师姓名：该课程的授课教师
    private String teacherName;
    // 选课容量上限：最多可选课人数
    private Integer capacity;
    // 当前已选人数：已选课学生数
    private Integer currentEnrolled;
    /** 剩余名额 = 容量 - 已选人数 */
    // 剩余名额：等于 容量(capacity) - 已选人数(currentEnrolled)，为 0 表示已满员
    private Integer remain;
    /** 课程状态：UNPUBLISHED 未发布 | PUBLISHED 已发布 */
    // 课程状态：UNPUBLISHED（未发布）/ PUBLISHED（已发布）
    private String status;
}
