// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JSR-303 参数校验注解 @NotBlank（用于非空校验）。
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 账号信息编辑请求（学号/工号与角色不可修改）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 编辑账号基本信息时前端提交的请求体；学号/工号与角色不允许修改，因此不包含这两个字段
public class AccountUpdateDTO {

    // @NotBlank：姓名不允许为 null、空串或纯空白字符
    @NotBlank(message = "姓名不能为空")
    // 姓名：用户新的真实姓名
    private String realName;

    /** 学生：性别 */
    // 性别：学生账号专属可编辑字段（男/女）
    private String gender;

    // 手机号：联系方式
    private String phone;
    // 院系：学生所属院系；教师账号也可填写
    private String department;

    /** 学生：专业 / 班级 / 入学年份 */
    // 专业：学生专属字段，所学专业名称
    private String major;
    // 班级：学生专属字段，所在班级名称
    private String className;
    // 入学年份：学生专属字段，入学年份（如 2023）
    private Integer enrollmentYear;
}
