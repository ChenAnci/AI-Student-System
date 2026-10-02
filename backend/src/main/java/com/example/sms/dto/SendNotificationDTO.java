// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）、
// JSR-303 参数校验注解 @NotBlank / @NotNull（非空校验），
// 以及 JDK 的 List（列表类型，承载接收学生 id 列表）。
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 手动发送通知请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 管理员或教师手动发送站内通知时，前端提交的请求体
public class SendNotificationDTO {

    // @NotBlank：通知标题不允许为空
    @NotBlank(message = "通知标题不能为空")
    // 通知标题：通知的标题文字
    private String title;

    // @NotBlank：通知内容不允许为空
    @NotBlank(message = "通知内容不能为空")
    // 通知正文：通知的详细文字内容
    private String content;

    // 接收对象：发送方必须显式选择接收方式（kind），不允许"无目标发送"
    // @NotNull：接收目标不允许为空
    @NotNull(message = "接收对象不能为空")
    // 接收目标：封装了接收方式（kind）及对应的目标参数（学生列表/班级/专业等）
    private Target target;

    // @Data：嵌套类同样使用 Lombok 自动生成样板方法
    @Data
    // 静态内部类：描述通知的接收范围（按学生 id、班级、专业、院系、课程或全员）
    public static class Target {
        /** STUDENT_IDS | CLASS | MAJOR | DEPARTMENT | COURSE | ALL */
        // @NotBlank：接收方式不允许为空
        @NotBlank(message = "接收方式不能为空")
        // 接收方式枚举：STUDENT_IDS（指定学生）/ CLASS（按班级）/ MAJOR（按专业）/
        // DEPARTMENT（按院系）/ COURSE（按课程）/ ALL（全部学生）
        private String kind;

        // kind = STUDENT_IDS 时使用：接收学生 id 列表（仅管理员；教师用此方式会被 service 层拒绝）
        // 学生 id 列表：kind 为 STUDENT_IDS 时使用；仅管理员可指定具体学生，教师使用会被 service 层拒绝
        private List<Long> studentIds;

        // kind = CLASS 时使用：班级名
        // 班级名：kind 为 CLASS 时使用，向该班级全体学生发送
        private String className;

        // kind = MAJOR 时使用：专业名
        // 专业名：kind 为 MAJOR 时使用，向该专业全体学生发送
        private String major;

        // kind = DEPARTMENT 时使用：院系名
        // 院系名：kind 为 DEPARTMENT 时使用，向该院系全体学生发送
        private String department;

        // kind = COURSE 时使用：课程 id（教师只能传自己授课的课程，service 层校验归属）
        // 课程 id：kind 为 COURSE 时使用，向选修该课程的学生发送；教师只能传自己授课的课程（service 层校验归属）
        private Long courseId;
    }
}
