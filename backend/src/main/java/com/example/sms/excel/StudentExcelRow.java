package com.example.sms.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 学生 Excel 导入/导出模型
 */
@Data
@ColumnWidth(18)
public class StudentExcelRow {

    // 学生姓名
    @ExcelProperty("姓名")
    private String realName;

    // 学号（留空时由系统自动生成）
    @ExcelProperty("学号（留空自动生成）")
    private String studentNo;

    // 性别
    @ExcelProperty("性别")
    private String gender;

    // 所属院系
    @ExcelProperty("院系")
    private String department;

    // 所学专业
    @ExcelProperty("专业")
    private String major;

    // 所在班级
    @ExcelProperty("班级")
    private String className;

    // 入学年份
    @ExcelProperty("入学年份")
    private Integer enrollmentYear;

    // 手机号
    @ExcelProperty("手机号")
    private String phone;
}
