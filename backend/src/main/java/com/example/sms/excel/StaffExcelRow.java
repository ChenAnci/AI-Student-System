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

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("工号（留空自动生成）")
    private String staffNo;

    @ExcelProperty("角色（TEACHER教师，留空默认教师）")
    private String roleType;

    @ExcelProperty("院系")
    private String department;

    @ExcelProperty("手机号")
    private String phone;
}
