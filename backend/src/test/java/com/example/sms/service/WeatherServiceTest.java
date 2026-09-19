package com.example.sms.service;

import com.example.sms.config.BaiduWeatherProperties;
import com.example.sms.vo.WeatherVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeatherServiceTest {

    private BaiduWeatherProperties props;
    private RestTemplate restTemplate;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        props = new BaiduWeatherProperties();
        restTemplate = mock(RestTemplate.class);
        service = new WeatherService(props, restTemplate);
    }

    private static final String OK_JSON = "{"
            + "\"status\":0,\"message\":\"success\","
            + "\"result\":{"
            + "\"location\":{\"country\":\"中国\",\"province\":\"上海市\",\"city\":\"上海市\",\"name\":\"上海市\",\"id\":\"310100\"},"
            + "\"now\":{\"text\":\"多云\",\"temp\":24,\"feels_like\":26,\"rh\":68,\"wind_class\":\"3级\",\"wind_dir\":\"东南风\",\"uptime\":\"20260919100000\"}"
            + "}}";

    @Test
    @DisplayName("百度返回成功：正确解析各字段")
    void parseOk() {
        props.setAk("test-ak");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(OK_JSON);
        WeatherVO vo = service.getNow();
        assertThat(vo).isNotNull();
        assertThat(vo.getCity()).isEqualTo("上海市");
        assertThat(vo.getText()).isEqualTo("多云");
        assertThat(vo.getTemp()).isEqualTo(24);
        assertThat(vo.getFeelsLike()).isEqualTo(26);
        assertThat(vo.getHumidity()).isEqualTo(68);
        assertThat(vo.getWindClass()).isEqualTo("3级");
        assertThat(vo.getWindDir()).isEqualTo("东南风");
        assertThat(vo.getUpdateTime()).isEqualTo("20260919100000");
    }

    @Test
    @DisplayName("缓存命中：第二次调用不再请求百度")
    void cacheHit() {
        props.setAk("test-ak");
        props.setCacheTtlSeconds(1800);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(OK_JSON);
        service.getNow();
        service.getNow();
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("AK 未配置：直接返回 null，不发起请求")
    void missingAk() {
        props.setAk("");
        WeatherVO vo = service.getNow();
        assertThat(vo).isNull();
        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("百度返回 status!=0：返回 null")
    void nonZeroStatus() {
        props.setAk("test-ak");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"status\":302,\"message\":\"AK 校验失败\"}");
        assertThat(service.getNow()).isNull();
    }

    @Test
    @DisplayName("HTTP 异常：返回 null，不抛异常")
    void networkError() {
        props.setAk("test-ak");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"));
        assertThat(service.getNow()).isNull();
    }
}
