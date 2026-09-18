package com.example.sms.util;

import com.alibaba.excel.EasyExcel;
import com.example.sms.common.BusinessException;
import com.example.sms.excel.StudentExcelRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExcelUtil 导入导出工具单元测试
 */
class ExcelUtilTest {

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        response = null;
    }

    private MockMultipartFile xlsxFile(byte[] bytes, String filename) {
        return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    /** 用 POI 手工构造：表头 + 数据行(2) + 空行(3) + 数据行(4)，用于校验行号与空行跳过 */
    private byte[] xlsxWithBlankRow() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Sheet1");
            String[] header = {"姓名", "学号（留空自动生成）", "性别", "院系", "专业", "班级", "入学年份", "手机号"};
            var headRow = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) headRow.createCell(i).setCellValue(header[i]);
            var row2 = sheet.createRow(1);
            row2.createCell(0).setCellValue("张三");
            row2.createCell(1).setCellValue("S20230001");
            // 第 3 行留空
            var row4 = sheet.createRow(3);
            row4.createCell(0).setCellValue("李四");
            row4.createCell(1).setCellValue("S20230002");
            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("读取：正确返回行号并跳过空行")
    /** 验证场景：读取 Excel 时返回真实行号（第 2、4 行），并跳过中间的空行 */
    void readWithRowNumbers_shouldReturnRowsWithRealRowNumbers() throws IOException {
        List<ExcelUtil.RowItem<StudentExcelRow>> items =
                ExcelUtil.readWithRowNumbers(xlsxFile(xlsxWithBlankRow(), "students.xlsx"), StudentExcelRow.class);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getRowNum()).isEqualTo(2);
        assertThat(items.get(0).getData().getRealName()).isEqualTo("张三");
        assertThat(items.get(0).getData().getStudentNo()).isEqualTo("S20230001");
        assertThat(items.get(1).getRowNum()).isEqualTo(4);
        assertThat(items.get(1).getData().getRealName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("读取：仅表头时返回空列表")
    /** 验证场景：Excel 只有表头没有数据行时，读取结果为空列表 */
    void readWithRowNumbers_shouldReturnEmptyWhenOnlyHeader() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Sheet1");
            var head = sheet.createRow(0);
            head.createCell(0).setCellValue("姓名");
            head.createCell(1).setCellValue("学号（留空自动生成）");
            var out = new ByteArrayOutputStream();
            wb.write(out);

            List<ExcelUtil.RowItem<StudentExcelRow>> items = ExcelUtil.readWithRowNumbers(
                    xlsxFile(out.toByteArray(), "students.xlsx"), StudentExcelRow.class);

            assertThat(items).isEmpty();
        }
    }

    @Test
    @DisplayName("读取：空文件被拒绝")
    /** 验证场景：上传空文件时被拒绝并给出"请选择要上传的 Excel 文件"提示 */
    void readWithRowNumbers_shouldRejectEmptyFile() {
        assertThatThrownBy(() -> ExcelUtil.readWithRowNumbers(
                new MockMultipartFile("file", "students.xlsx", "application/octet-stream", new byte[0]),
                StudentExcelRow.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择要上传的 Excel 文件");
    }

    @Test
    @DisplayName("读取：非 Excel 扩展名被拒绝")
    /** 验证场景：上传非 .xlsx/.xls 扩展名的文件时被拒绝 */
    void readWithRowNumbers_shouldRejectNonExcelExtension() {
        MockMultipartFile txt = new MockMultipartFile("file", "students.txt", "text/plain",
                "hello".getBytes());
        assertThatThrownBy(() -> ExcelUtil.readWithRowNumbers(txt, StudentExcelRow.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 .xlsx / .xls");
    }

    @Test
    @DisplayName("导出：设置响应头并生成可解析的 xlsx")
    /** 验证场景：导出时正确设置 ContentType 与下载文件名响应头，且生成的 xlsx 内容可回读解析 */
    void write_shouldSetHeadersAndWriteValidXlsx() {
        List<StudentExcelRow> rows = List.of(buildStudent("S20230001", "张三"));
        ExcelUtil.write(response, "学生列表", StudentExcelRow.class, rows);

        assertThat(response.getContentType())
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition"))
                .startsWith("attachment;filename=")
                .endsWith(".xlsx");
        List<StudentExcelRow> parsed = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(StudentExcelRow.class).sheet().doReadSync();
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).getStudentNo()).isEqualTo("S20230001");
    }

    private StudentExcelRow buildStudent(String no, String name) {
        StudentExcelRow row = new StudentExcelRow();
        row.setStudentNo(no);
        row.setRealName(name);
        return row;
    }
}
