package com.example.sms.service; // 声明包名：本测试类位于 service 包，与被测 WeatherService 同包

// import 区：引入百度天气配置类（AK 与缓存时长配置）与天气返回 VO
import com.example.sms.config.BaiduWeatherProperties;
import com.example.sms.vo.WeatherVO;

// import 区：引入 JUnit5 生命周期/测试注解与 Spring 的 RestTemplate（用于模拟调用百度天气 HTTP 接口）
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

// import 区：静态导入 AssertJ 断言、Mockito 参数匹配器与 Mockito 打桩/校验方法
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeatherServiceTest { // 测试类声明：不依赖 Spring 容器，手动创建被测对象与 Mock

    private BaiduWeatherProperties props; // 声明百度天气配置对象（被测服务的构造参数）
    private RestTemplate restTemplate; // 声明 Mock 的 RestTemplate（拦截对百度天气的 HTTP 调用）
    private WeatherService service; // 声明被测服务对象

    @BeforeEach // JUnit5 生命周期注解：每个测试方法执行前运行
    void setUp() { // 每个用例执行前的公共初始化：重建一套全新的被测对象与 Mock
        props = new BaiduWeatherProperties(); // 创建配置对象（每次都是新实例，避免用例间串数据）
        restTemplate = mock(RestTemplate.class); // 用 Mockito 创建 RestTemplate 的 Mock 对象
        service = new WeatherService(props, restTemplate); // 手动构造被测服务：注入配置与 Mock 的 RestTemplate
    }

    private static final String OK_JSON = "{" // 定义百度天气接口返回的"成功"JSON 样本（status=0 表示成功）
            + "\"status\":0,\"message\":\"success\"," // status 为 0 表示调用成功
            + "\"result\":{" // result 节点开始（天气数据主体）
            + "\"location\":{\"country\":\"中国\",\"province\":\"上海市\",\"city\":\"上海市\",\"name\":\"上海市\",\"id\":\"310100\"}," // 位置信息：城市为上海市，城市编码 310100
            + "\"now\":{\"text\":\"多云\",\"temp\":24,\"feels_like\":26,\"rh\":68,\"wind_class\":\"3级\",\"wind_dir\":\"东南风\",\"uptime\":\"20260919100000\"}" // 实时天气：多云、24℃、体感26、湿度68、3级东南风、更新时间
            + "}}"; // 关闭 result 与最外层 JSON 对象

    @Test
    @DisplayName("百度返回成功：正确解析各字段")
    void parseOk() { // 用例1：百度返回成功时能正确解析各字段
        props.setAk("test-ak"); // 配置一个测试用的 AK（否则服务因 AK 缺失直接返回 null）
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(OK_JSON); // 打桩：任何 URL 调用都返回成功 JSON 样本
        WeatherVO vo = service.getNow(); // 调用被测方法：获取当前天气
        assertThat(vo).isNotNull(); // 断言：返回的天气对象不为空
        assertThat(vo.getCity()).isEqualTo("上海市"); // 断言：城市解析为"上海市"
        assertThat(vo.getText()).isEqualTo("多云"); // 断言：天气现象解析为"多云"
        assertThat(vo.getTemp()).isEqualTo(24); // 断言：温度解析为 24
        assertThat(vo.getFeelsLike()).isEqualTo(26); // 断言：体感温度解析为 26
        assertThat(vo.getHumidity()).isEqualTo(68); // 断言：相对湿度解析为 68
        assertThat(vo.getWindClass()).isEqualTo("3级"); // 断言：风力等级解析为"3级"
        assertThat(vo.getWindDir()).isEqualTo("东南风"); // 断言：风向解析为"东南风"
        assertThat(vo.getUpdateTime()).isEqualTo("20260919100000"); // 断言：更新时间解析为"20260919100000"
    }

    @Test
    @DisplayName("缓存命中：第二次调用不再请求百度")
    void cacheHit() { // 用例2：缓存命中时第二次调用不再请求百度
        props.setAk("test-ak"); // 配置测试用 AK
        props.setCacheTtlSeconds(1800); // 配置缓存有效期 1800 秒（使缓存生效）
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(OK_JSON); // 打桩：第一次调用返回成功 JSON
        service.getNow(); // 第一次调用：未命中缓存，会发起 HTTP 请求并写入缓存
        service.getNow(); // 第二次调用：命中缓存，应直接返回缓存结果
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class)); // 断言：HTTP 请求只发起 1 次（第二次未请求百度，缓存生效）
    }

    @Test
    @DisplayName("AK 未配置：直接返回 null，不发起请求")
    void missingAk() { // 用例3：AK 未配置时直接返回 null 且不发起请求
        props.setAk(""); // 把 AK 配置为空字符串（模拟未配置）
        WeatherVO vo = service.getNow(); // 调用被测方法
        assertThat(vo).isNull(); // 断言：返回 null（无 AK 不请求，防止浪费与报错）
        verify(restTemplate, never()).getForObject(anyString(), eq(String.class)); // 断言：HTTP 请求从未发起
    }

    @Test
    @DisplayName("百度返回 status!=0：返回 null")
    void nonZeroStatus() { // 用例4：百度返回非 0 状态码时返回 null
        props.setAk("test-ak"); // 配置测试用 AK
        when(restTemplate.getForObject(anyString(), eq(String.class))) // 打桩：HTTP 调用返回
                .thenReturn("{\"status\":302,\"message\":\"AK 校验失败\"}"); // 返回 status=302 的失败 JSON（如 AK 校验失败）
        assertThat(service.getNow()).isNull(); // 断言：业务上返回 null（失败时不给前端脏数据）
    }

    @Test
    @DisplayName("HTTP 异常：返回 null，不抛异常")
    void networkError() { // 用例5：HTTP 调用抛异常时返回 null 而不向上抛异常
        props.setAk("test-ak"); // 配置测试用 AK
        when(restTemplate.getForObject(anyString(), eq(String.class))) // 打桩：HTTP 调用
                .thenThrow(new RuntimeException("timeout")); // 模拟网络超时抛出的运行时异常
        assertThat(service.getNow()).isNull(); // 断言：调用方得到 null（服务内部捕获异常，保证接口稳定不 500）
    }
}
