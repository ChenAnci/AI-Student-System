// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor
// （自动生成样板代码：getter/setter、无参构造、全参构造）。
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号导入结果（含随机初始密码，供导入者线下分发）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// @NoArgsConstructor：自动生成无参构造方法
@NoArgsConstructor
// @AllArgsConstructor：自动生成包含全部字段的有参构造方法
@AllArgsConstructor
// 批量导入学生/教师账号后，返回给导入者（教学秘书）的每条导入结果，
// 其中包含系统随机生成的初始密码，由导入者线下分发给对应人员
public class AccountImportResultVO {
    // 姓名：被导入账号的用户姓名
    private String realName;
    // 账号：学号（学生）或工号（教师/管理员）
    private String userNo;
    // 初始密码：系统为账号随机生成的初始登录密码，需线下安全分发给用户本人
    private String initPassword;
}
