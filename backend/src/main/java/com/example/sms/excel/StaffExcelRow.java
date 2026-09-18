package com.example.sms.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 教职工 Excel 导入/导出模型
 */
@Data
@ColumnWidth(18)
public class StaffExcelRow {

    // 教职工姓名
    @ExcelProperty("姓名")
    private String realName;

    // 工号（留空时由系统自动生成）
    @ExcelProperty("工号（留空自动生成）")
    private String staffNo;

    // 角色：TEACHER 教师（留空默认教师）
    @ExcelProperty("角色（TEACHER教师，留空默认教师）")
    private String roleType;

    // 所属院系
    @ExcelProperty("院系")
    private String department;

    // 手机号
    @ExcelProperty("手机号")
    private String phone;
}
