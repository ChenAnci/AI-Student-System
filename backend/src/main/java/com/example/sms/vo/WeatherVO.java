package com.example.sms.vo;

import lombok.Data;

/**
 * 实时天气返回体：百度天气 result.location + result.now 的精选字段。
 * 调用逻辑：WeatherService 解析百度 JSON 后填充，经 WeatherController 以 Result<WeatherVO> 返回前端。
 */
@Data
public class WeatherVO {
    /** 城市名（如 上海市） */
    private String city;
    /** 天气现象（如 多云） */
    private String text;
    /** 当前温度（℃） */
    private Integer temp;
    /** 体感温度（℃） */
    private Integer feelsLike;
    /** 风力等级（如 3级） */
    private String windClass;
    /** 风向（如 东南风） */
    private String windDir;
    /** 相对湿度（%） */
    private Integer humidity;
    /** 数据更新时间（yyyyMMddHHmmss） */
    private String updateTime;
}
