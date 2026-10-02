// 包声明：本类位于 excel 包，存放 Excel 导入/导出使用的行数据模型
package com.example.sms.excel;

// ---------- import 区域说明 ----------
// 此处导入 EasyExcel（阿里巴巴开源 Excel 处理库）的注解：
// @ExcelProperty（字段与 Excel 表头的映射）、@ColumnWidth（列宽设置），
// 以及 Lombok 的 @Data。
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 教职工 Excel 导入/导出模型
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// @ColumnWidth(18)：EasyExcel 注解，导出时统一设置列宽为 18 个字符宽度
@ColumnWidth(18)
// 教职工账号批量导入/导出时，Excel 表格中每一行数据对应的模型
public class StaffExcelRow {

    // 教职工姓名
    // @ExcelProperty("姓名")：将该字段映射到 Excel 表头"姓名"列
    @ExcelProperty("姓名")
    // 姓名：教职工真实姓名
    private String realName;

    // 工号（留空时由系统自动生成）
    // @ExcelProperty：映射到 Excel 表头"工号（留空自动生成）"列
    @ExcelProperty("工号（留空自动生成）")
    // 工号：教职工登录账号；留空时由系统自动生成
    private String staffNo;

    // 角色：TEACHER 教师（留空默认教师）
    // @ExcelProperty：映射到 Excel 表头"角色（TEACHER教师，留空默认教师）"列
    @ExcelProperty("角色（TEACHER教师，留空默认教师）")
    // 角色类型：TEACHER（教师），留空时系统默认按教师处理
    private String roleType;

    // 所属院系
    // @ExcelProperty("院系")：映射到 Excel 表头"院系"列
    @ExcelProperty("院系")
    // 院系：教职工所属院系名称
    private String department;

    // 手机号
    // @ExcelProperty("手机号")：映射到 Excel 表头"手机号"列
    @ExcelProperty("手机号")
    // 手机号：联系方式
    private String phone;
}
