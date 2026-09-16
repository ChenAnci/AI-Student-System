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

    @NotNull(message = "接收对象不能为空")
    private Target target;

    @Data
    public static class Target {
        /** STUDENT_IDS | CLASS | MAJOR | DEPARTMENT | COURSE | ALL */
        @NotBlank(message = "接收方式不能为空")
        private String kind;

        private List<Long> studentIds;
        private String className;
        private String major;
        private String department;
        private Long courseId;
    }
}
