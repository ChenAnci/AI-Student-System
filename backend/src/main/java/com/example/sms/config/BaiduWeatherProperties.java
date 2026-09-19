package com.example.sms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百度地图「天气查询」接口配置。
 * 调用逻辑：Spring 启动时绑定 application.yml 中 baidu.weather.* 前缀（AK 经环境变量 BAIDU_WEATHER_AK 注入），
 * WeatherService 构造时注入本类读取配置。
 * 为什么：AK 只存后端、禁止硬编码；cache-ttl-seconds 用于控制缓存时长以保护个人免费配额。
 */
@Data
@Component
@ConfigurationProperties(prefix = "baidu.weather")
public class BaiduWeatherProperties {

    /** 百度地图 AK（环境变量 BAIDU_WEATHER_AK 注入；为空则天气卡片整体降级） */
    private String ak = "";

    /** 行政区划编码（上海市 = 310100） */
    private String districtId = "310100";

    /** 结果缓存时长（秒），默认 1800 = 30 分钟 */
    private long cacheTtlSeconds = 1800;
}
