package com.example.sms.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩 Excel 导入/导出模型
 */
@Data
@ColumnWidth(18)
public class GradeExcelRow {

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("总评成绩（0-100）")
    private BigDecimal score;

    @ExcelProperty("标记（NORMAL正常/DEFER缓考/ABSENT缺考/CHEAT舞弊）")
    private String mark;
}
