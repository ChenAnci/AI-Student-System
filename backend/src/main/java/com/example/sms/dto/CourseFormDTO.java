// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）、
// JSR-303 参数校验注解 @NotBlank / @NotNull（非空校验），
// 以及 JDK 的高精度小数 BigDecimal（用于学分等精确数值）。
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 课程创建/编辑请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 创建课程或编辑课程时，前端提交的请求体（创建/编辑共用同一 DTO）
public class CourseFormDTO {

    // @NotBlank：课程编号不允许为空
    @NotBlank(message = "课程编号不能为空")
    // 课程编号：教务系统分配的唯一课程编码（如 CS101）
    private String courseCode;

    // @NotBlank：课程名称不允许为空
    @NotBlank(message = "课程名称不能为空")
    // 课程名称：课程标题，选课列表展示用
    private String courseName;

    // @NotNull：学分不允许为空
    @NotNull(message = "学分不能为空")
    // 学分：该课程的学分值（如 3.0），BigDecimal 保证精度
    private BigDecimal credit;

    // @NotNull：学时不允许为空
    @NotNull(message = "学时不能为空")
    // 总学时：课程教学总课时数（整数，单位：学时）
    private Integer hours;

    // 课程封面图 URL：课程卡片展示的图片地址（可选）
    private String coverImageUrl;
    // 上课时间：文字描述排课时间，如 "周一 3-4 节"（可选）
    private String schedule;
    // 上课地点：文字描述上课教室/地点，如 "A101 机房"（可选）
    private String location;

    /** 授课教师 ID（可选）：教学秘书创建/编辑课程时可指定，教师本人创建时忽略 */
    // 授课教师 ID：外键，关联 staff 表；教学秘书可指定，教师本人创建课程时该字段被忽略
    private Long teacherId;

    // @NotNull：容量不允许为空
    @NotNull(message = "容量不能为空")
    // 选课容量上限：该课程最多允许选课的学生人数
    private Integer capacity;
}
