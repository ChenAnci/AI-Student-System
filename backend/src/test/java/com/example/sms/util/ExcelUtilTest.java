package com.example.sms.util; // 声明包名：本测试类位于 util 包，与被测工具类 ExcelUtil 同包

// import 区：引入 EasyExcel（Excel 读写）、业务异常、学生 Excel 行模型与 POI 工作簿对象（用于手工构造测试用 xlsx）
import com.alibaba.excel.EasyExcel;
import com.example.sms.common.BusinessException;
import com.example.sms.excel.StudentExcelRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// import 区：引入 JUnit5 生命周期/测试注解与 Spring 的 Mock 响应/文件上传对象
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

// import 区：引入 IO 流工具类与 List 集合
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

// import 区：静态导入 AssertJ 断言方法
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExcelUtil 导入导出工具单元测试
 */
class ExcelUtilTest { // 测试类声明：纯工具类测试，不依赖 Spring 容器与数据库

    private MockHttpServletResponse response; // 声明 Mock 响应对象（用于导出用例捕获输出）

    @BeforeEach // JUnit5 生命周期注解：每个测试方法执行前运行
    void setUp() { // 每个用例执行前的公共初始化
        response = new MockHttpServletResponse(); // 新建 Mock 响应对象（每次独立，避免残留数据）
    }

    @AfterEach // JUnit5 生命周期注解：每个测试方法执行后运行
    void tearDown() { // 每个用例执行后的清理
        response = null; // 置空响应引用，释放对象
    }

    private MockMultipartFile xlsxFile(byte[] bytes, String filename) { // 工具方法：把字节数组包装成 xlsx 上传文件
        return new MockMultipartFile("file", filename, // 构造 Mock 文件上传对象：表单字段 file、指定文件名
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes); // 指定 xlsx MIME 类型并携带字节内容
    }

    /** 用 POI 手工构造：表头 + 数据行(2) + 空行(3) + 数据行(4)，用于校验行号与空行跳过 */
    private byte[] xlsxWithBlankRow() throws IOException { // 工具方法：用 POI 手工构造一个含空行的测试 xlsx
        try (XSSFWorkbook wb = new XSSFWorkbook()) { // 创建 POI 工作簿（try-with-resources 自动释放资源）
            var sheet = wb.createSheet("Sheet1"); // 创建工作表 Sheet1
            String[] header = {"姓名", "学号（留空自动生成）", "性别", "院系", "专业", "班级", "入学年份", "手机号"}; // 定义表头内容（与 StudentExcelRow 列对应）
            var headRow = sheet.createRow(0); // 创建第 1 行（索引 0）作为表头行
            for (int i = 0; i < header.length; i++) headRow.createCell(i).setCellValue(header[i]); // 循环把每个表头文本写入对应单元格
            var row2 = sheet.createRow(1); // 创建第 2 行（索引 1）作为第一条数据行
            row2.createCell(0).setCellValue("张三"); // 第 2 行第 1 列：姓名"张三"
            row2.createCell(1).setCellValue("S20230001"); // 第 2 行第 2 列：学号"S20230001"
            // 第 3 行留空
            var row4 = sheet.createRow(3); // 直接创建第 4 行（索引 3）作为第二条数据行（第 3 行索引 2 留空，模拟空行）
            row4.createCell(0).setCellValue("李四"); // 第 4 行第 1 列：姓名"李四"
            row4.createCell(1).setCellValue("S20230002"); // 第 4 行第 2 列：学号"S20230002"
            var out = new ByteArrayOutputStream(); // 创建内存输出流
            wb.write(out); // 把工作簿写入内存输出流
            return out.toByteArray(); // 返回 xlsx 的字节数组
        }
    }

    @Test
    @DisplayName("读取：正确返回行号并跳过空行")
    /** 验证场景：读取 Excel 时返回真实行号（第 2、4 行），并跳过中间的空行 */
    void readWithRowNumbers_shouldReturnRowsWithRealRowNumbers() throws IOException { // 用例1：读取时返回真实行号并跳过空行
        List<ExcelUtil.RowItem<StudentExcelRow>> items = // 调用被测方法：读取 Excel（返回带真实行号的行项列表）
                ExcelUtil.readWithRowNumbers(xlsxFile(xlsxWithBlankRow(), "students.xlsx"), StudentExcelRow.class); // 传入含空行的 xlsx 与目标行类型

        assertThat(items).hasSize(2); // 断言：解析出 2 条有效数据（空行被跳过）
        assertThat(items.get(0).getRowNum()).isEqualTo(2); // 断言：第一条数据对应 Excel 第 2 行
        assertThat(items.get(0).getData().getRealName()).isEqualTo("张三"); // 断言：第一条数据姓名为张三
        assertThat(items.get(0).getData().getStudentNo()).isEqualTo("S20230001"); // 断言：第一条数据学号为 S20230001
        assertThat(items.get(1).getRowNum()).isEqualTo(4); // 断言：第二条数据对应 Excel 第 4 行（跳过了第 3 行空行）
        assertThat(items.get(1).getData().getRealName()).isEqualTo("李四"); // 断言：第二条数据姓名为李四
    }

    @Test
    @DisplayName("读取：仅表头时返回空列表")
    /** 验证场景：Excel 只有表头没有数据行时，读取结果为空列表 */
    void readWithRowNumbers_shouldReturnEmptyWhenOnlyHeader() throws IOException { // 用例2：仅表头时返回空列表
        try (XSSFWorkbook wb = new XSSFWorkbook()) { // 创建 POI 工作簿（try-with-resources 自动释放资源）
            var sheet = wb.createSheet("Sheet1"); // 创建工作表 Sheet1
            var head = sheet.createRow(0); // 创建第 1 行（索引 0）作为表头行
            head.createCell(0).setCellValue("姓名"); // 表头第 1 列：姓名
            head.createCell(1).setCellValue("学号（留空自动生成）"); // 表头第 2 列：学号（留空自动生成）
            var out = new ByteArrayOutputStream(); // 创建内存输出流
            wb.write(out); // 把工作簿写入内存输出流

            List<ExcelUtil.RowItem<StudentExcelRow>> items = ExcelUtil.readWithRowNumbers( // 调用被测方法：读取仅含表头的 Excel
                    xlsxFile(out.toByteArray(), "students.xlsx"), StudentExcelRow.class); // 传入字节数组与目标行类型

            assertThat(items).isEmpty(); // 断言：读取结果为空列表（无数据行）
        }
    }

    @Test
    @DisplayName("读取：空文件被拒绝")
    /** 验证场景：上传空文件时被拒绝并给出"请选择要上传的 Excel 文件"提示 */
    void readWithRowNumbers_shouldRejectEmptyFile() { // 用例3：空文件被拒绝
        assertThatThrownBy(() -> ExcelUtil.readWithRowNumbers( // 断言：读取空文件时抛出异常
                new MockMultipartFile("file", "students.xlsx", "application/octet-stream", new byte[0]), // 构造 0 字节的空文件
                StudentExcelRow.class)) // 传入目标行类型
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("请选择要上传的 Excel 文件"); // 断言：异常信息提示请选择要上传的 Excel 文件
    }

    @Test
    @DisplayName("读取：非 Excel 扩展名被拒绝")
    /** 验证场景：上传非 .xlsx/.xls 扩展名的文件时被拒绝 */
    void readWithRowNumbers_shouldRejectNonExcelExtension() { // 用例4：非 Excel 扩展名被拒绝
        MockMultipartFile txt = new MockMultipartFile("file", "students.txt", "text/plain", // 构造一个 .txt 扩展名的文本文件
                "hello".getBytes()); // 文件内容为"hello"
        assertThatThrownBy(() -> ExcelUtil.readWithRowNumbers(txt, StudentExcelRow.class)) // 断言：读取 .txt 文件时抛出异常
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("仅支持 .xlsx / .xls"); // 断言：异常信息提示仅支持 .xlsx / .xls 文件
    }

    @Test
    @DisplayName("导出：设置响应头并生成可解析的 xlsx")
    /** 验证场景：导出时正确设置 ContentType 与下载文件名响应头，且生成的 xlsx 内容可回读解析 */
    void write_shouldSetHeadersAndWriteValidXlsx() { // 用例5：导出时设置响应头并生成合法 xlsx
        List<StudentExcelRow> rows = List.of(buildStudent("S20230001", "张三")); // 准备 1 行待导出的学生数据
        ExcelUtil.write(response, "学生列表", StudentExcelRow.class, rows); // 调用被测方法：把学生数据导出到响应对象（文件名"学生列表"）

        assertThat(response.getContentType()) // 断言：响应 ContentType
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // 包含 xlsx 的 MIME 类型
        assertThat(response.getHeader("Content-Disposition")) // 断言：Content-Disposition 响应头
                .startsWith("attachment;filename=") // 以附件下载文件名标记开头
                .endsWith(".xlsx"); // 以 .xlsx 结尾（正确生成下载文件名）
        List<StudentExcelRow> parsed = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())) // 回读响应体中的 Excel 字节流
                .head(StudentExcelRow.class).sheet().doReadSync(); // 指定表头类型并同步读取所有行
        assertThat(parsed).hasSize(1); // 断言：回读得到 1 行数据
        assertThat(parsed.get(0).getStudentNo()).isEqualTo("S20230001"); // 断言：第一行学号为 S20230001（内容正确）
    }

    private StudentExcelRow buildStudent(String no, String name) { // 工具方法：构造一行学生导出数据
        StudentExcelRow row = new StudentExcelRow(); // 创建学生 Excel 行对象
        row.setStudentNo(no); // 设置学号
        row.setRealName(name); // 设置姓名
        return row; // 返回该行数据
    }
}
