package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 手动发送通知请求
 */
@Data
public class SendNotificationDTO {

    @NotBlank(message = "通知标题不能为空")
    private String title;

    @NotBlank(message = "通知内容不能为空")
    private String content;

    // 接收对象：发送方必须显式选择接收方式（kind），不允许"无目标发送"
    @NotNull(message = "接收对象不能为空")
    private Target target;

    @Data
    public static class Target {
        /** STUDENT_IDS | CLASS | MAJOR | DEPARTMENT | COURSE | ALL */
        @NotBlank(message = "接收方式不能为空")
        private String kind;

        // kind = STUDENT_IDS 时使用：接收学生 id 列表（仅管理员；教师用此方式会被 service 层拒绝）
        private List<Long> studentIds;

        // kind = CLASS 时使用：班级名
        private String className;

        // kind = MAJOR 时使用：专业名
        private String major;

        // kind = DEPARTMENT 时使用：院系名
        private String department;

        // kind = COURSE 时使用：课程 id（教师只能传自己授课的课程，service 层校验归属）
        private Long courseId;
    }
}
