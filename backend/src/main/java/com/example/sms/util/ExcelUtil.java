package com.example.sms.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.example.sms.common.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * EasyExcel 导入导出工具
 */
public class ExcelUtil {

    /** 带 Excel 行号的数据包装（表头为第 1 行） */
    public static class RowItem<T> {
        /** Excel 实际行号（从 1 开始，表头为第 1 行） */
        private final int rowNum;
        /** 该行解析出的业务数据 */
        private final T data;

        public RowItem(int rowNum, T data) {
            this.rowNum = rowNum;
            this.data = data;
        }

        /** 返回 Excel 行号（用于错误提示时定位到具体行） */
        public int getRowNum() {
            return rowNum;
        }

        /** 返回该行解析出的数据对象 */
        public T getData() {
            return data;
        }
    }

    /**
     * 读取 Excel 文件，返回带行号的数据（自动跳过全空行）
     *
     * 调用逻辑：导入接口（如用户/学生批量导入）接收前端上传的 MultipartFile 后调用本方法，
     * 得到 List&lt;RowItem&lt;T&gt;&gt; 后由 Service 逐行做业务校验（重复、格式、存在性）再批量入库；
     * 校验失败的条目借助 rowNum 提示"第 N 行数据错误"，便于用户对照 Excel 修改。
     * 为什么用 EasyExcel 流式读取：底层 SAX 逐行解析，内存占用与文件行数无关，
     * 不会像 POI 的 XSSFWorkbook 那样一次性把整个工作簿载入内存，适合大文件导入场景。
     */
    public static <T> List<RowItem<T>> readWithRowNumbers(MultipartFile file, Class<T> clazz) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx / .xls 格式的文件");
        }
        List<RowItem<T>> result = new ArrayList<>();
        try {
            // clazz 通过 @ExcelProperty 注解完成"表头列名 → 实体字段"的映射，EasyExcel 按列名匹配读取
            EasyExcel.read(file.getInputStream(), clazz, new AnalysisEventListener<T>() {
                @Override
                public void invoke(T data, AnalysisContext context) {
                    if (data == null || isBlankRow(data)) return;
                    // readRowHolder().getRowIndex() 为 0 起始（表头行 index=0），Excel 中行号 = index + 1
                    result.add(new RowItem<>(context.readRowHolder().getRowIndex() + 1, data));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().doRead();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件解析失败，请使用系统提供的模板填写");
        }
        return result;
    }

    /**
     * 导出 Excel 到响应流（表头列同样由 clazz 的 @ExcelProperty 决定）
     *
     * 调用逻辑：导出接口先从数据库查出目标数据（List&lt;T&gt;），再调用本方法写入 HttpServletResponse，
     * 前端收到附件下载响应直接触发浏览器下载（a 标签 / axios blob 均可）。
     * 为什么：Content-Disposition 中文件名用 URLEncoder 编码并把 + 替换为 %20，
     * 兼容中文文件名（避免中文乱码或下载失败），同时显式声明 Excel 二进制 MIME 类型。
     */
    public static <T> void write(HttpServletResponse response, String fileName, Class<T> clazz, List<T> rows) {
        try {
            // 声明响应为 Excel 二进制流并设置附件下载头（文件名需 URL 编码，兼容中文）
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + encode + ".xlsx");
            // 按 clazz 映射写表头与数据到响应输出流
            EasyExcel.write(response.getOutputStream(), clazz).sheet("Sheet1").doWrite(rows);
        } catch (IOException e) {
            throw new BusinessException("文件导出失败");
        }
    }

    /** 判断某行数据是否全为空（用于跳过空行） */
    private static boolean isBlankRow(Object data) {
        // 反射遍历实体所有字段：任一字段非 null 且非空白字符串即视为"有内容"，否则整行视为空行
        for (Field field : data.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(data);
                if (value == null) continue;
                if (value instanceof String && ((String) value).isBlank()) continue;
                return false;
            } catch (IllegalAccessException ignored) {
                // 私有字段访问失败按无值处理，不阻断导入流程
            }
        }
        return true;
    }
}
