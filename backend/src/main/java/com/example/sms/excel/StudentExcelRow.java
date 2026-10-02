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
 * 学生 Excel 导入/导出模型
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// @ColumnWidth(18)：EasyExcel 注解，导出时统一设置列宽为 18 个字符宽度
@ColumnWidth(18)
// 学生账号批量导入/导出时，Excel 表格中每一行数据对应的模型
public class StudentExcelRow {

    // 学生姓名
    // @ExcelProperty("姓名")：将该字段映射到 Excel 表头"姓名"列
    @ExcelProperty("姓名")
    // 姓名：学生真实姓名
    private String realName;

    // 学号（留空时由系统自动生成）
    // @ExcelProperty：映射到 Excel 表头"学号（留空自动生成）"列
    @ExcelProperty("学号（留空自动生成）")
    // 学号：学生登录账号；留空时由系统自动生成
    private String studentNo;

    // 性别
    // @ExcelProperty("性别")：映射到 Excel 表头"性别"列
    @ExcelProperty("性别")
    // 性别：男 / 女
    private String gender;

    // 所属院系
    // @ExcelProperty("院系")：映射到 Excel 表头"院系"列
    @ExcelProperty("院系")
    // 院系：学生所属院系名称
    private String department;

    // 所学专业
    // @ExcelProperty("专业")：映射到 Excel 表头"专业"列
    @ExcelProperty("专业")
    // 专业：学生所学专业名称
    private String major;

    // 所在班级
    // @ExcelProperty("班级")：映射到 Excel 表头"班级"列
    @ExcelProperty("班级")
    // 班级：学生所在班级名称
    private String className;

    // 入学年份
    // @ExcelProperty("入学年份")：映射到 Excel 表头"入学年份"列
    @ExcelProperty("入学年份")
    // 入学年份：学生入学的年份（整数，如 2023）
    private Integer enrollmentYear;

    // 手机号
    // @ExcelProperty("手机号")：映射到 Excel 表头"手机号"列
    @ExcelProperty("手机号")
    // 手机号：联系方式
    private String phone;
}
