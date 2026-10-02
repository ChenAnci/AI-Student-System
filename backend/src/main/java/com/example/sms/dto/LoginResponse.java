// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）。
import lombok.Data;

/**
 * 登录响应
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 用户登录成功后，服务端返回给前端的数据对象（携带令牌与用户基本信息）
public class LoginResponse {

    /** JWT 登录令牌（后续请求放入 Authorization 头） */
    // JWT 登录令牌：后续请求需放入 Authorization 请求头中携带，用于身份认证
    private String token;
    // 用户 ID：登录用户在数据库中的主键 id
    private Long userId;
    /** 学号（学生）或工号（教职工/管理员） */
    // 账号：学号（学生）或工号（教职工/管理员），用于前端展示当前登录人
    private String userNo;
    // 姓名：用户真实姓名
    private String realName;
    /** ADMIN 管理员 | TEACHER 教师 | STUDENT 学生 */
    // 角色类型：ADMIN（管理员）/ TEACHER（教师）/ STUDENT（学生），前端据此渲染不同界面
    private String roleType;
    /** 学生专属：姓名之外的基本信息（前端首页展示） */
    // 院系：学生专属信息，用于前端首页展示
    private String department;
    // 专业：学生专属信息，用于前端首页展示
    private String major;
    // 班级：学生专属信息，用于前端首页展示
    private String className;
}
