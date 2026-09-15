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
        private final int rowNum;
        private final T data;

        public RowItem(int rowNum, T data) {
            this.rowNum = rowNum;
            this.data = data;
        }

        public int getRowNum() {
            return rowNum;
        }

        public T getData() {
            return data;
        }
    }

    /**
     * 读取 Excel 文件，返回带行号的数据（自动跳过全空行）
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

    /** 导出 Excel 到响应流 */
    public static <T> void write(HttpServletResponse response, String fileName, Class<T> clazz, List<T> rows) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + encode + ".xlsx");
            EasyExcel.write(response.getOutputStream(), clazz).sheet("Sheet1").doWrite(rows);
        } catch (IOException e) {
            throw new BusinessException("文件导出失败");
        }
    }

    /** 判断某行数据是否全为空（用于跳过空行） */
    private static boolean isBlankRow(Object data) {
        for (Field field : data.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(data);
                if (value == null) continue;
                if (value instanceof String && ((String) value).isBlank()) continue;
                return false;
            } catch (IllegalAccessException ignored) {
            }
        }
        return true;
    }
}
