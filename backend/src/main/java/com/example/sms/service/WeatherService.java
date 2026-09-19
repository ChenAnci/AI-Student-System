package com.example.sms.service;

import com.example.sms.config.BaiduWeatherProperties;
import com.example.sms.vo.WeatherVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时天气服务：调用百度地图「天气查询」接口并做 30 分钟 TTL 缓存。
 * 调用逻辑：WeatherController.now → getNow()：命中缓存直接返回；
 * 否则请求 api.map.baidu.com/weather/v1 → 解析 result.now/location → 写缓存后返回。
 * 为什么：AK 只存后端不暴露前端；缓存保护个人免费配额（默认约 1000 次/天），
 * AK 未配置或接口异常统一降级返回 null，天气卡片显示占位、不影响页面其余数据。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String API_URL = "https://api.map.baidu.com/weather/v1/";

    private final BaiduWeatherProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存：key 为行政区划编码（当前仅上海一个区划），value 为数据 + 写入时间戳 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 生产构造：内部构建带 5s 超时的 RestTemplate（Spring 仅此一个 public 构造，自动装配） */
    @Autowired
    public WeatherService(BaiduWeatherProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 测试专用构造：注入 mock RestTemplate，跳过真实 HTTP（包内可见，测试类同包使用） */
    WeatherService(BaiduWeatherProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    /**
     * 获取上海实时天气；不可用时返回 null（降级，不抛异常）。
     * 调用逻辑：WeatherController.now 每次请求调用；AK 未配置直接返回 null；
     * 缓存未过期直接返回缓存；否则 fetch 拉取并回填缓存。
     */
    public WeatherVO getNow() {
        if (props.getAk().isEmpty()) {
            log.warn("BAIDU_WEATHER_AK 未配置，天气卡片降级为空");
            return null;
        }
        String key = props.getDistrictId();
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.ts < props.getCacheTtlSeconds() * 1000) {
            return entry.vo;
        }
        WeatherVO vo = fetch(key);
        if (vo != null) {
            cache.put(key, new CacheEntry(vo, now));
        }
        return vo;
    }

    /** 请求百度接口并解析为 WeatherVO；任何异常返回 null（降级） */
    private WeatherVO fetch(String districtId) {
        try {
            String url = API_URL + "?district_id=" + districtId + "&data_type=now&ak=" + props.getAk();
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            if (root.path("status").asInt() != 0) {
                log.warn("百度天气返回异常 status={} message={}",
                        root.path("status").asInt(), root.path("message").asText());
                return null;
            }
            JsonNode result = root.path("result");
            JsonNode now = result.path("now");
            WeatherVO vo = new WeatherVO();
            vo.setCity(result.path("location").path("name").asText());
            vo.setText(now.path("text").asText());
            vo.setTemp(now.path("temp").asInt());
            vo.setFeelsLike(now.path("feels_like").asInt());
            vo.setWindClass(now.path("wind_class").asText());
            vo.setWindDir(now.path("wind_dir").asText());
            vo.setHumidity(now.path("rh").asInt());
            vo.setUpdateTime(now.path("uptime").asText());
            return vo;
        } catch (Exception e) {
            log.warn("百度天气接口调用失败，本次降级为空: {}", e.getMessage());
            return null;
        }
    }

    /** 缓存条目：天气数据 + 写入时间戳 */
    private static class CacheEntry {
        final WeatherVO vo;
        final long ts;

        CacheEntry(WeatherVO vo, long ts) {
            this.vo = vo;
            this.ts = ts;
        }
    }
}
