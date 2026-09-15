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

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("学号（留空自动生成）")
    private String studentNo;

    @ExcelProperty("性别")
    private String gender;

    @ExcelProperty("院系")
    private String department;

    @ExcelProperty("专业")
    private String major;

    @ExcelProperty("班级")
    private String className;

    @ExcelProperty("入学年份")
    private Integer enrollmentYear;

    @ExcelProperty("手机号")
    private String phone;
}
