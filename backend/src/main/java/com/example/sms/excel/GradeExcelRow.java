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

    // 学号（导入时按学号关联到对应学生）
    @ExcelProperty("学号")
    private String studentNo;

    // 学生姓名
    @ExcelProperty("姓名")
    private String realName;

    // 总评成绩，范围 0-100
    @ExcelProperty("总评成绩（0-100）")
    private BigDecimal score;

    // 成绩标记：NORMAL 正常 / DEFER 缓考 / ABSENT 缺考 / CHEAT 舞弊
    @ExcelProperty("标记（NORMAL正常/DEFER缓考/ABSENT缺考/CHEAT舞弊）")
    private String mark;
}
