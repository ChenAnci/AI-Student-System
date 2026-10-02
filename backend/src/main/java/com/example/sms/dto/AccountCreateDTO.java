// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JSR-303 参数校验注解 @NotBlank（用于非空校验）。
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 教学秘书添加账号请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教学秘书（管理员）批量/逐个创建新账号时，前端提交的请求体
public class AccountCreateDTO {

    // @NotBlank：姓名不允许为 null、空串或纯空白字符，否则校验失败并返回该提示信息
    @NotBlank(message = "姓名不能为空")
    // 姓名：新账号用户的真实姓名
    private String realName;

    /** TEACHER | STUDENT */
    // @NotBlank：角色不允许为空
    @NotBlank(message = "角色不能为空")
    // 角色类型：TEACHER（教师）| STUDENT（学生），决定创建的是教职工账号还是学生账号
    private String roleType;

    /** 学生专属字段 */
    // 性别：仅创建学生账号时使用（男/女）；创建教师账号时忽略
    private String gender;
    // 院系：学生所属院系名称，仅学生账号使用
    private String department;
    // 专业：学生所学专业名称，仅学生账号使用
    private String major;
    // 班级：学生所在班级名称，仅学生账号使用
    private String className;
    // 入学年份：学生入学年份（如 2023），仅学生账号使用
    private Integer enrollmentYear;
    // 手机号：联系方式，便于系统发送通知
    private String phone;
}
