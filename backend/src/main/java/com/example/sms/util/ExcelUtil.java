package com.example.sms.util; // 声明该类所在包：util 包，存放通用工具类

// ---- import 区域：引入 EasyExcel 流式读写、MultipartFile 上传、HttpServletResponse 响应、反射 Field、URL 编码等 ----
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
public class ExcelUtil { // 静态工具类：提供 Excel 读取（带行号）与导出两个方法

    /** 带 Excel 行号的数据包装（表头为第 1 行） */
    public static class RowItem<T> { // 内部包装类：把解析出的数据与其在 Excel 中的真实行号绑定，便于错误提示定位
        /** Excel 实际行号（从 1 开始，表头为第 1 行） */
        private final int rowNum; // 记录该数据对应的 Excel 行号（final：构造后不可变）
        /** 该行解析出的业务数据 */
        private final T data; // 记录该行解析出的业务对象（泛型，对应实体类型）

        public RowItem(int rowNum, T data) { // 构造方法：由读取逻辑创建，同时保存行号与数据
            this.rowNum = rowNum; // 赋值行号
            this.data = data; // 赋值数据
        }

        /** 返回 Excel 行号（用于错误提示时定位到具体行） */
        public int getRowNum() { // 行号取值器：Service 校验失败时可提示"第 N 行数据错误"
            return rowNum; // 返回保存的行号
        }

        /** 返回该行解析出的数据对象 */
        public T getData() { // 数据取值器：返回该行对应的业务对象
            return data; // 返回保存的数据
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
    public static <T> List<RowItem<T>> readWithRowNumbers(MultipartFile file, Class<T> clazz) { // 通用导入入口：file 为上传文件，clazz 为数据实体类型
        if (file == null || file.isEmpty()) { // 前置校验：文件为空则直接拒绝导入
            throw new BusinessException("请选择要上传的 Excel 文件"); // 抛出业务异常（code=400），由全局处理器转成统一提示返回前端
        }
        String name = file.getOriginalFilename(); // 获取上传文件的原始文件名（用于判断扩展名）
        if (name == null || !(name.endsWith(".xlsx") || name.endsWith(".xls"))) { // 校验扩展名：仅接受 Excel 的两种后缀
            throw new BusinessException("仅支持 .xlsx / .xls 格式的文件"); // 非 Excel 文件直接拒绝，避免解析报错
        }
        List<RowItem<T>> result = new ArrayList<>(); // 准备结果集合：按读取顺序保存每行数据及其行号
        try { // try 块：把解析过程包起来，统一捕获并转换为业务异常
            // clazz 通过 @ExcelProperty 注解完成"表头列名 → 实体字段"的映射，EasyExcel 按列名匹配读取
            EasyExcel.read(file.getInputStream(), clazz, new AnalysisEventListener<T>() { // 注册流式读取：以 SAX 方式逐行回调，内存占用与行数无关
                @Override // 重写逐行回调方法
                public void invoke(T data, AnalysisContext context) { // 每解析出一行数据触发一次（含表头映射后的业务对象）
                    if (data == null || isBlankRow(data)) return; // 空数据或全空行直接跳过，不进入结果列表
                    // readRowHolder().getRowIndex() 为 0 起始（表头行 index=0），Excel 中行号 = index + 1
                    result.add(new RowItem<>(context.readRowHolder().getRowIndex() + 1, data)); // 记录"行号+1"（转成 Excel 真实行号）与数据
                }

                @Override // 重写解析完成回调方法
                public void doAfterAllAnalysed(AnalysisContext context) { // 全部行解析完毕后的回调（本工具无需额外处理，保留空实现）
                }
            }).sheet().doRead(); // 读取第一个工作表并开始解析
        } catch (BusinessException e) { // 捕获业务异常：原样上抛，保留原始业务语义
            throw e; // 直接重新抛出，不让外层 catch 吞掉业务错误
        } catch (Exception e) { // 捕获其它所有异常（IO、格式错误、字段映射失败等）
            throw new BusinessException("文件解析失败，请使用系统提供的模板填写"); // 统一转成友好提示，不暴露底层解析错误
        }
        return result; // 返回带行号的解析结果，供 Service 层逐行校验
    }

    /**
     * 导出 Excel 到响应流（表头列同样由 clazz 的 @ExcelProperty 决定）
     *
     * 调用逻辑：导出接口先从数据库查出目标数据（List&lt;T&gt;），再调用本方法写入 HttpServletResponse，
     * 前端收到附件下载响应直接触发浏览器下载（a 标签 / axios blob 均可）。
     * 为什么：Content-Disposition 中文件名用 URLEncoder 编码并把 + 替换为 %20，
     * 兼容中文文件名（避免中文乱码或下载失败），同时显式声明 Excel 二进制 MIME 类型。
     */
    public static <T> void write(HttpServletResponse response, String fileName, Class<T> clazz, List<T> rows) { // 通用导出入口：向 HTTP 响应写出 Excel 附件
        try { // try 块：把响应写入过程包起来，IO 失败统一转业务异常
            // 声明响应为 Excel 二进制流并设置附件下载头（文件名需 URL 编码，兼容中文）
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // 设置 MIME 类型为 Excel（.xlsx）二进制流
            response.setCharacterEncoding("utf-8"); // 设置响应字符编码为 UTF-8，防止中文文件名乱码
            String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20"); // URL 编码文件名，并把空格编码产生的 + 还原为 %20（浏览器下载兼容）
            response.setHeader("Content-Disposition", "attachment;filename=" + encode + ".xlsx"); // 声明"附件下载"并给出编码后的文件名（触发浏览器另存为）
            // 按 clazz 映射写表头与数据到响应输出流
            EasyExcel.write(response.getOutputStream(), clazz).sheet("Sheet1").doWrite(rows); // 将数据按实体注解写为 Excel 并直接输出到响应流
        } catch (IOException e) { // 捕获写入响应流时的 IO 异常
            throw new BusinessException("文件导出失败"); // 转成业务异常统一返回，避免堆栈外泄
        }
    }

    /** 判断某行数据是否全为空（用于跳过空行） */
    private static boolean isBlankRow(Object data) { // 私有工具方法：反射检查实体的所有字段是否都为空
        // 反射遍历实体所有字段：任一字段非 null 且非空白字符串即视为"有内容"，否则整行视为空行
        for (Field field : data.getClass().getDeclaredFields()) { // 遍历实体类声明的所有字段（含 private）
            try { // try 块：反射访问私有字段可能抛 IllegalAccessException
                field.setAccessible(true); // 开放私有字段访问权限（Java 9+ 模块系统下类内使用仍可）
                Object value = field.get(data); // 读取该字段在当前对象上的值
                if (value == null) continue; // 字段值为 null 视为空，继续检查下一个字段
                if (value instanceof String && ((String) value).isBlank()) continue; // 字符串字段为空白字符也视为空
                return false; // 存在任一"有内容"字段：该行不是空行
            } catch (IllegalAccessException ignored) { // 捕获字段访问异常：按"无值"处理，不阻断整个导入流程
                // 私有字段访问失败按无值处理，不阻断导入流程
            }
        }
        return true; // 所有字段都为空：判定为全空行，调用方跳过该行
    }
}
