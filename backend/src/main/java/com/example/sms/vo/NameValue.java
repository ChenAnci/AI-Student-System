// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor
// （自动生成样板代码：getter/setter、无参构造、全参构造）。
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用名称-数值统计项
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// @NoArgsConstructor：自动生成无参构造方法
@NoArgsConstructor
// @AllArgsConstructor：自动生成包含全部字段的有参构造方法
@AllArgsConstructor
// 通用"名称-数值"统计项：用于图表类接口（饼图/柱状图/排行等）的通用数据单元
public class NameValue {

    // 名称：统计维度名称（如专业名、课程名、分数段等）
    private String name;
    // 数值：对应的统计数量
    private Long value;
}
