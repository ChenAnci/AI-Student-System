// 包声明：本类位于 excel 包，存放 Excel 导入/导出使用的行数据模型
package com.example.sms.excel;

// ---------- import 区域说明 ----------
// 此处导入 EasyExcel（阿里巴巴开源 Excel 处理库）的注解：
// @ExcelProperty（字段与 Excel 表头的映射）、@ColumnWidth（列宽设置），
// 以及 Lombok 的 @Data 和 JDK 的高精度小数 BigDecimal（用于成绩等精确数值）。
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩 Excel 导入/导出模型
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// @ColumnWidth(18)：EasyExcel 注解，导出时统一设置列宽为 18 个字符宽度
@ColumnWidth(18)
// 成绩批量导入/导出时，Excel 表格中每一行数据对应的模型
public class GradeExcelRow {

    // 学号（导入时按学号关联到对应学生）
    // @ExcelProperty("学号")：将该字段映射到 Excel 表头"学号"列
    @ExcelProperty("学号")
    // 学号：导入时按学号在系统中查找并关联到对应学生
    private String studentNo;

    // 学生姓名
    // @ExcelProperty("姓名")：映射到 Excel 表头"姓名"列
    @ExcelProperty("姓名")
    // 姓名：学生真实姓名
    private String realName;

    // 总评成绩，范围 0-100
    // @ExcelProperty：映射到 Excel 表头"总评成绩（0-100）"列
    @ExcelProperty("总评成绩（0-100）")
    // 总评成绩：0~100 分制，BigDecimal 保证精度
    private BigDecimal score;

    // 成绩标记：NORMAL 正常 / DEFER 缓考 / ABSENT 缺考 / CHEAT 舞弊
    // @ExcelProperty：映射到 Excel 表头"标记（NORMAL正常/DEFER缓考/ABSENT缺考/CHEAT舞弊）"列
    @ExcelProperty("标记（NORMAL正常/DEFER缓考/ABSENT缺考/CHEAT舞弊）")
    // 考试标记：NORMAL（正常）/ DEFER（缓考）/ ABSENT（缺考）/ CHEAT（舞弊）
    private String mark;
}
