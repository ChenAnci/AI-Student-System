package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入配置绑定所需的 Lombok 与 Spring Boot 配置属性注解 ----
import lombok.Data; // Lombok 的 @Data 注解：自动生成 getter/setter/toString/equals/hashCode
import org.springframework.boot.context.properties.ConfigurationProperties; // 配置属性绑定注解：将 yml 配置前缀映射到类字段
import org.springframework.stereotype.Component; // @Component 注解：将该类注册为 Spring 容器管理的组件

/**
 * 百度地图「天气查询」接口配置。
 * 调用逻辑：Spring 启动时绑定 application.yml 中 baidu.weather.* 前缀（AK 经环境变量 BAIDU_WEATHER_AK 注入），
 * WeatherService 构造时注入本类读取配置。
 * 为什么：AK 只存后端、禁止硬编码；cache-ttl-seconds 用于控制缓存时长以保护个人免费配额。
 */
@Data // 自动生成字段的 getter/setter/toString/equals/hashCode，方便取值与打印
@Component // 注册为 Spring Bean：可被其它组件（如 WeatherService）通过构造器注入使用
@ConfigurationProperties(prefix = "baidu.weather") // 绑定 application.yml 中 baidu.weather.* 前缀的配置项到下方字段
public class BaiduWeatherProperties { // 百度天气接口配置属性类

    /** 百度地图 AK（环境变量 BAIDU_WEATHER_AK 注入；为空则天气卡片整体降级） */
    private String ak = ""; // AK（访问密钥）字段：默认空字符串；为空时天气功能整体降级不展示

    /** 行政区划编码（上海市 = 310100） */
    private String districtId = "310100"; // 行政区划编码：默认上海市（310100），用于按地区查询天气

    /** 结果缓存时长（秒），默认 1800 = 30 分钟 */
    private long cacheTtlSeconds = 1800; // 天气结果缓存时长（秒）：默认 30 分钟，控制调用频率以保护免费接口配额
}
