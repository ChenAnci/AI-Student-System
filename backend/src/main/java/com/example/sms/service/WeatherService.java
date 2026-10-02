package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入百度天气配置、天气视图对象、Jackson、日志、Spring 相关注解与 Java 并发工具 =====
import com.example.sms.config.BaiduWeatherProperties; // 百度天气配置属性类（AK、区划、缓存 TTL）
import com.example.sms.vo.WeatherVO; // 天气视图对象
import com.fasterxml.jackson.databind.JsonNode; // Jackson JSON 树节点：解析百度天气响应
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson JSON 解析器
import org.slf4j.Logger; // slf4j 日志接口
import org.slf4j.LoggerFactory; // slf4j 日志工厂：获取 Logger 实例
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.http.client.SimpleClientHttpRequestFactory; // Spring HTTP 客户端请求工厂：设置超时
import org.springframework.stereotype.Service; // Spring 服务层注解
import org.springframework.web.client.RestTemplate; // Spring 的 HTTP 客户端模板：发起 GET 请求
import java.util.Map; // 键值映射接口
import java.util.concurrent.ConcurrentHashMap; // 并发安全的哈希表：天气缓存

/**
 * 实时天气服务：调用百度地图「天气查询」接口并做 30 分钟 TTL 缓存。
 * 调用逻辑：WeatherController.now → getNow()：命中缓存直接返回；
 * 否则请求 api.map.baidu.com/weather/v1 → 解析 result.now/location → 写缓存后返回。
 * 为什么：AK 只存后端不暴露前端；缓存保护个人免费配额（默认约 1000 次/天），
 * AK 未配置或接口异常统一降级返回 null，天气卡片显示占位、不影响页面其余数据。
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class WeatherService { // 实时天气服务类：获取天气数据并带 30 分钟本地缓存

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class); // 创建本类的日志记录器（记录告警/降级信息）
    private static final String API_URL = "https://api.map.baidu.com/weather/v1/"; // 百度天气接口地址

    private final BaiduWeatherProperties props; // 百度天气配置（AK、区划 id、缓存 TTL）
    private final RestTemplate restTemplate; // HTTP 客户端模板：请求百度天气接口
    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson 解析器：解析天气 JSON 响应

    /** 缓存：key 为行政区划编码（当前仅上海一个区划），value 为数据 + 写入时间戳 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>(); // 天气缓存：并发安全，key 为区划编码

    /** 生产构造：内部构建带 5s 超时的 RestTemplate（Spring 仅此一个 public 构造，自动装配） */
    @Autowired // 标记为自动装配构造（Spring 通过该构造注入依赖）
    public WeatherService(BaiduWeatherProperties props) { // 生产环境构造：注入配置并构建带超时的 RestTemplate
        this.props = props; // 保存百度天气配置
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); // 创建请求工厂（用于设置超时）
        factory.setConnectTimeout(5000); // 连接超时 5 秒（避免第三方接口卡死请求线程）
        factory.setReadTimeout(5000); // 读取超时 5 秒
        this.restTemplate = new RestTemplate(factory); // 用带超时配置的工厂创建 RestTemplate
    }

    /** 测试专用构造：注入 mock RestTemplate，跳过真实 HTTP（包内可见，测试类同包使用） */
    WeatherService(BaiduWeatherProperties props, RestTemplate restTemplate) { // 测试构造：注入 mock 的 RestTemplate
        this.props = props; // 保存配置
        this.restTemplate = restTemplate; // 使用外部注入的 RestTemplate（测试时替换为 mock）
    }

    /**
     * 获取上海实时天气；不可用时返回 null（降级，不抛异常）。
     * 调用逻辑：WeatherController.now 每次请求调用；AK 未配置直接返回 null；
     * 缓存未过期直接返回缓存；否则 fetch 拉取并回填缓存。
     */
    public WeatherVO getNow() { // 获取实时天气：优先返回未过期缓存，否则拉取新数据并回填缓存
        if (props.getAk().isEmpty()) { // AK（应用密钥）未配置
            log.warn("BAIDU_WEATHER_AK 未配置，天气卡片降级为空"); // 记录告警日志
            return null; // 降级返回 null（前端显示占位，不影响其他页面数据）
        }
        String key = props.getDistrictId(); // 取行政区划编码作为缓存 key
        CacheEntry entry = cache.get(key); // 读取缓存条目
        long now = System.currentTimeMillis(); // 取当前时间戳（判断缓存是否过期）
        if (entry != null && now - entry.ts < props.getCacheTtlSeconds() * 1000) { // 缓存存在且未超过 TTL 有效期
            return entry.vo; // 直接返回缓存中的天气数据
        }
        WeatherVO vo = fetch(key); // 缓存过期或未命中：调用百度接口拉取最新天气
        if (vo != null) { // 拉取成功
            cache.put(key, new CacheEntry(vo, now)); // 写入缓存（数据 + 当前时间戳）
        }
        return vo; // 返回拉取结果（失败时 fetch 内部已返回 null）
    }

    /** 请求百度接口并解析为 WeatherVO；任何异常返回 null（降级） */
    private WeatherVO fetch(String districtId) { // 请求百度天气接口并解析天气数据
        try { // 捕获网络/解析异常，统一降级返回 null
            String url = API_URL + "?district_id=" + districtId + "&data_type=now&ak=" + props.getAk(); // 拼装请求 URL（区划 id + 实时数据 + AK）
            String body = restTemplate.getForObject(url, String.class); // GET 请求获取响应字符串
            if (body == null) { // 响应为空
                return null; // 降级返回 null
            }
            JsonNode root = objectMapper.readTree(body); // 解析响应为 JSON 树
            if (root.path("status").asInt() != 0) { // 接口返回状态码非 0（业务错误）
                log.warn("百度天气返回异常 status={} message={}", // 记录告警日志（含状态码与错误信息）
                        root.path("status").asInt(), root.path("message").asText()); // 输出状态码与 message
                return null; // 降级返回 null
            }
            JsonNode result = root.path("result"); // 取 result 节点
            JsonNode now = root.path("result").path("now"); // 取"now"实时天气节点
            WeatherVO vo = new WeatherVO(); // 创建天气视图对象
            vo.setCity(result.path("location").path("name").asText()); // 城市名称（location.name）
            vo.setText(now.path("text").asText()); // 天气现象（如"晴"）
            vo.setTemp(now.path("temp").asInt()); // 当前温度
            vo.setFeelsLike(now.path("feels_like").asInt()); // 体感温度
            vo.setWindClass(now.path("wind_class").asText()); // 风力等级
            vo.setWindDir(now.path("wind_dir").asText()); // 风向
            vo.setHumidity(now.path("rh").asInt()); // 相对湿度
            vo.setUpdateTime(now.path("uptime").asText()); // 数据更新时间
            return vo; // 返回解析完成的天气数据
        } catch (Exception e) { // 请求或解析出现任何异常
            log.warn("百度天气接口调用失败，本次降级为空: {}", e.getMessage()); // 记录告警日志
            return null; // 降级返回 null（前端显示占位，不影响页面其余数据）
        }
    }

    /** 缓存条目：天气数据 + 写入时间戳 */
    private static class CacheEntry { // 天气缓存条目内部类
        final WeatherVO vo; // 缓存的天气数据
        final long ts; // 写入缓存时的时间戳（判断 TTL 用）

        CacheEntry(WeatherVO vo, long ts) { // 构造函数：保存数据与时间戳
            this.vo = vo; // 保存天气数据
            this.ts = ts; // 保存写入时间戳
        }
    }
}
