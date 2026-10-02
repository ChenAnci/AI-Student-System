// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）。
import lombok.Data;

/**
 * 实时天气返回体：百度天气 result.location + result.now 的精选字段。
 * 调用逻辑：WeatherService 解析百度 JSON 后填充，经 WeatherController 以 Result<WeatherVO> 返回前端。
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 实时天气数据对象：字段取自百度天气接口 result.location 与 result.now 的精选字段；
// 调用链路：WeatherService 解析百度天气 JSON 后填充本对象，经 WeatherController 以 Result<WeatherVO> 返回前端
public class WeatherVO {
    /** 城市名（如 上海市） */
    // 城市名：天气所属城市，如"上海市"
    private String city;
    /** 天气现象（如 多云） */
    // 天气现象：如"多云""晴""小雨"等
    private String text;
    /** 当前温度（℃） */
    // 当前温度：单位摄氏度（℃）
    private Integer temp;
    /** 体感温度（℃） */
    // 体感温度：人体实际感受温度，单位摄氏度（℃）
    private Integer feelsLike;
    /** 风力等级（如 3级） */
    // 风力等级：如"3级""4级"等
    private String windClass;
    /** 风向（如 东南风） */
    // 风向：如"东南风""西北风"等
    private String windDir;
    /** 相对湿度（%） */
    // 相对湿度：空气中水汽含量百分比（%）
    private Integer humidity;
    /** 数据更新时间（yyyyMMddHHmmss） */
    // 数据更新时间：格式为 yyyyMMddHHmmss（如 20260919120000）
    private String updateTime;
}
