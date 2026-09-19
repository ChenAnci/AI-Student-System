# 百度实时天气卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在系统三个角色页面（学生仪表盘 / 教师统计页 / 教秘统计页）接入上海实时天气卡片，数据源为百度地图开放平台天气查询接口，AK 仅在后端环境变量注入。

**Architecture:** 后端新增 `/api/weather` 代理接口（`WeatherController` + `WeatherService`），内部调用 `api.map.baidu.com/weather/v1`，响应做 30 分钟 TTL 内存缓存，AK 缺失或接口异常一律降级返回 null。前端抽通用 `WeatherCard.vue` 组件在三个页面复用；失败时卡片内显示"天气服务暂不可用"，不影响页面其它数据。

**Tech Stack:** Spring Boot 2.7 (Java 17, RestTemplate, Jackson) / Vue3 + TypeScript + Element Plus / 百度地图天气接口 `api.map.baidu.com/weather/v1`（district_id=310100 上海市）。

设计文档：`docs/superpowers/specs/2026-09-19-baidu-weather-design.md`

---

### Task 1: 后端配置（application.yml + 属性类）

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/example/sms/config/BaiduWeatherProperties.java`

- [ ] **Step 1: application.yml 增加 baidu.weather 配置**

在 `ai:` 配置块之后、`app:` 配置块之前插入（保留原有注释与缩进风格）：

```yaml
# 百度天气实时接口（AK 通过环境变量注入，未配置时天气卡片降级为空）
baidu:
  weather:
    ak: ${BAIDU_WEATHER_AK:}
    district-id: 310100
    cache-ttl-seconds: 1800
```

- [ ] **Step 2: 新建 BaiduWeatherProperties 属性类**

创建 `backend/src/main/java/com/example/sms/config/BaiduWeatherProperties.java`：

```java
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
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/resources/application.yml backend/src/main/java/com/example/sms/config/BaiduWeatherProperties.java
git commit -m "feat(weather): 新增百度天气配置属性与 application.yml 配置"
```

---

### Task 2: WeatherService（TDD：先写测试）

**Files:**
- Create: `backend/src/main/java/com/example/sms/vo/WeatherVO.java`
- Test: `backend/src/test/java/com/example/sms/service/WeatherServiceTest.java`
- Create: `backend/src/main/java/com/example/sms/service/WeatherService.java`

- [ ] **Step 1: 先建 WeatherVO（测试依赖的返回类型）**

创建 `backend/src/main/java/com/example/sms/vo/WeatherVO.java`：

```java
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
```

- [ ] **Step 2: 写失败测试 WeatherServiceTest**

创建 `backend/src/test/java/com/example/sms/service/WeatherServiceTest.java`：

```java
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
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q test -Dtest=WeatherServiceTest`
Expected: 编译失败 —— `WeatherService` 不存在。

- [ ] **Step 4: 实现 WeatherService**

创建 `backend/src/main/java/com/example/sms/service/WeatherService.java`：

```java
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
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q test -Dtest=WeatherServiceTest`
Expected: 5 个测试全部 PASS（Tests run: 5）。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/example/sms/vo/WeatherVO.java backend/src/test/java/com/example/sms/service/WeatherServiceTest.java backend/src/main/java/com/example/sms/service/WeatherService.java
git commit -m "feat(weather): 百度天气服务（解析+30min缓存+降级），含单测5项"
```

---

### Task 3: WeatherController + 拦截器登记

**Files:**
- Create: `backend/src/main/java/com/example/sms/controller/WeatherController.java`
- Modify: `backend/src/main/java/com/example/sms/config/JwtInterceptor.java`（ROLE_RULES 增加一行）

- [ ] **Step 1: 新建 WeatherController**

创建 `backend/src/main/java/com/example/sms/controller/WeatherController.java`：

```java
package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.service.WeatherService;
import com.example.sms.vo.WeatherVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实时天气接口（百度天气，上海）。
 * 调用逻辑：三个角色页面（学生仪表盘/教师统计/教秘统计）加载时请求本接口展示天气卡片；
 * AK 未配置或百度异常时 data 为 null，前端显示"天气服务暂不可用"。
 */
@Api(tags = "实时天气")
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @ApiOperation("获取上海实时天气")
    @GetMapping
    public Result<WeatherVO> now() {
        return Result.success(weatherService.getNow());
    }
}
```

- [ ] **Step 2: JwtInterceptor 登记角色规则**

在 `JwtInterceptor.java` 的 `ROLE_RULES` 中 `/api/stats/teacher` 一行之后新增一行（三个角色都可访问）：

```java
            {"/api/stats/admin", "ADMIN"},
            {"/api/stats/teacher", "TEACHER"},
            {"/api/weather", "STUDENT,TEACHER,ADMIN"},
```

- [ ] **Step 3: 编译并跑全量后端测试**

Run: `mvn -q test`
Expected: 全部测试通过（原 66+5=71 项左右），`BUILD SUCCESS`。

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/example/sms/controller/WeatherController.java backend/src/main/java/com/example/sms/config/JwtInterceptor.java
git commit -m "feat(weather): /api/weather 接口与拦截器角色登记"
```

---

### Task 4: 前端类型与 API

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/weather.ts`

- [ ] **Step 1: types/index.ts 追加 WeatherInfo**

在 `frontend/src/types/index.ts` 末尾追加：

```ts
/** 实时天气信息（后端 /api/weather；data 为 null 表示天气服务不可用） */
export interface WeatherInfo {
  city: string
  text: string
  temp: number
  feelsLike: number
  windClass: string
  windDir: string
  humidity: number
  updateTime: string
}
```

- [ ] **Step 2: 新建 api/weather.ts**

创建 `frontend/src/api/weather.ts`：

```ts
// ===== 实时天气接口（weather） =====
// 职责：封装后端 /api/weather 代理接口；AK 在服务端，前端不接触密钥。
import http from './http'
import type { WeatherInfo } from '@/types'

/** 获取上海实时天气（后端降级时 data 为 null） */
export function getWeather() {
  return http.get('/weather') as Promise<WeatherInfo>
}
```

- [ ] **Step 3: 类型检查**

Run: `npx vue-tsc --noEmit`
Expected: 无类型错误。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/types/index.ts frontend/src/api/weather.ts
git commit -m "feat(weather): 前端天气类型与 API 封装"
```

---

### Task 5: WeatherCard.vue 通用组件

**Files:**
- Create: `frontend/src/components/WeatherCard.vue`

- [ ] **Step 1: 新建组件**

创建 `frontend/src/components/WeatherCard.vue`：

```vue
<template>
  <el-card shadow="hover" header="实时天气">
    <div v-if="loading" class="w-card">天气加载中…</div>
    <div v-else-if="weather" class="w-card">
      <div class="w-main">
        <span class="w-icon">{{ iconFor(weather.text) }}</span>
        <span class="w-text">{{ weather.text }}</span>
        <span class="w-temp">{{ weather.temp }}°C</span>
      </div>
      <div class="w-sub">
        <span>{{ weather.city }}</span>
        <span>体感 {{ weather.feelsLike }}°C</span>
        <span>{{ weather.windDir }} {{ weather.windClass }}</span>
        <span>湿度 {{ weather.humidity }}%</span>
        <span>更新 {{ fmtTime(weather.updateTime) }}</span>
      </div>
    </div>
    <div v-else class="w-card error">天气服务暂不可用</div>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 实时天气卡片（通用）：调用后端 /api/weather 代理接口，三个角色页面复用。
 * 降级策略：接口失败或 data 为 null 时展示"天气服务暂不可用"，不影响页面其余数据。
 */
import { onMounted, ref } from 'vue'
import { getWeather } from '@/api/weather'
import type { WeatherInfo } from '@/types'

const loading = ref(true)
const weather = ref<WeatherInfo | null>(null)

// 天气现象 → 简单文字图标（子串匹配，兼容"多云转晴"等组合文案）
function iconFor(text: string) {
  if (!text) return '🌡️'
  if (text.includes('晴')) return '☀️'
  if (text.includes('多云')) return '⛅'
  if (text.includes('阴')) return '☁️'
  if (text.includes('雷')) return '⛈️'
  if (text.includes('雪')) return '❄️'
  if (text.includes('雨')) return '🌧️'
  if (text.includes('雾') || text.includes('霾')) return '🌫️'
  return '🌡️'
}

// 百度更新时间 yyyyMMddHHmmss → HH:mm
function fmtTime(uptime: string) {
  if (!uptime || uptime.length < 12) return uptime || ''
  return `${uptime.slice(8, 10)}:${uptime.slice(10, 12)}`
}

onMounted(async () => {
  try {
    weather.value = await getWeather()
  } catch {
    weather.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.w-card {
  min-height: 64px;
}
.w-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.w-icon {
  font-size: 28px;
}
.w-text {
  font-size: 16px;
  color: #303133;
}
.w-temp {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.w-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #909399;
}
.error {
  color: #909399;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/components/WeatherCard.vue
git commit -m "feat(weather): WeatherCard 通用天气卡片组件"
```

---

### Task 6: 三个页面接入 WeatherCard

**Files:**
- Modify: `frontend/src/views/student/DashboardView.vue`
- Modify: `frontend/src/views/teacher/TeacherStatsView.vue`
- Modify: `frontend/src/views/admin/StatsView.vue`

- [ ] **Step 1: 学生仪表盘 DashboardView.vue**

在 `<script setup>` 中 `import EChart from '@/components/EChart.vue'` 之后新增一行：

```ts
import WeatherCard from '@/components/WeatherCard.vue'
```

在模板最外层 `<div v-loading="loading">` 之后、`<!-- 顶部统计卡片 -->` 之前插入：

```html
    <el-row :gutter="16" class="mt16">
      <el-col :span="24">
        <WeatherCard />
      </el-col>
    </el-row>
```

- [ ] **Step 2: 教师统计页 TeacherStatsView.vue**

在 `<script setup>` 中 `import EChart from '@/components/EChart.vue'` 之后新增一行：

```ts
import WeatherCard from '@/components/WeatherCard.vue'
```

在模板 `<div v-loading="loading">` 之后、`<div class="stats-toolbar">` 之前插入：

```html
    <el-row :gutter="16" class="mt16">
      <el-col :span="24">
        <WeatherCard />
      </el-col>
    </el-row>
```

- [ ] **Step 3: 教秘统计页 admin/StatsView.vue**

在 `<script setup>` 中 `import EChart from '@/components/EChart.vue'` 之后新增一行：

```ts
import WeatherCard from '@/components/WeatherCard.vue'
```

在模板 `<div v-loading="loading">` 之后、`<!-- 统计卡片 -->` 之前插入：

```html
    <el-row :gutter="16" class="mt16">
      <el-col :span="24">
        <WeatherCard />
      </el-col>
    </el-row>
```

- [ ] **Step 4: 类型检查**

Run: `npx vue-tsc --noEmit`
Expected: 无类型错误。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/student/DashboardView.vue frontend/src/views/teacher/TeacherStatsView.vue frontend/src/views/admin/StatsView.vue
git commit -m "feat(weather): 三个角色页面接入天气卡片"
```

---

### Task 7: 验证与文档

**Files:**
- Modify: `README.md`（功能特性、技术栈、环境变量表）
- Modify: `docs/项目解析-答辩指南.md`（技术栈表、亮点、功能清单）

- [ ] **Step 1: 冒烟验证后端**

带 AK 重启后端：`$env:BAIDU_WEATHER_AK='ql0irM7pCYFqcyqzp0j1nDYpnqrN5518'; mvn spring-boot:run`
再登录取 token，请求 `GET /api/weather`：
- AK 有效 → 返回 `{code:200,data:{city:"上海市",text:"多云",...}}`
- 未配置 AK（重启不带该变量）→ 返回 `{code:200,data:null}`（降级）

- [ ] **Step 2: 冒烟验证前端**

`npm run dev`，用学生 `S20230001` / 教师 `T1001` / 教秘 `admin`（密码 `123456`）登录三个角色，确认各页面天气卡片正常显示或降级提示；浏览器控制台无报错。

- [ ] **Step 3: 更新 README.md**

- 功能特性"实时天气"：新增一条"仪表盘实时天气卡片（上海，百度天气）"
- 技术栈行追加 `+ 百度天气 API`
- 环境变量表新增 `BAIDU_WEATHER_AK` 行（可选，未配置天气卡片降级）

- [ ] **Step 4: 更新答辩文档**

- 技术栈表新增「百度天气 API」行（打个比方：实时天气预报）
- 亮点或功能清单补充"三端仪表盘实时天气卡片"

- [ ] **Step 5: 提交**

```bash
git add README.md docs/项目解析-答辩指南.md
git commit -m "docs: README 与答辩文档同步百度天气卡片"
```

---

## Self-Review 记录

- **Spec 覆盖**：后端代理+缓存（Task1-3）、前端组件（Task4-5）、三页面接入（Task6）、验证与文档（Task7）—— 与设计文档 4/5/6 节一一对应。
- **占位符扫描**：无 TBD/TODO；每个代码步骤含完整代码。
- **类型一致性**：`WeatherVO` 字段名（city/text/temp/feelsLike/windClass/windDir/humidity/updateTime）在 Service、Test、前端 `WeatherInfo`、`WeatherCard.vue` 中完全一致；`getWeather()` 返回 `Promise<WeatherInfo>` 与组件 `ref<WeatherInfo|null>` 一致；`getNow()` 返回 `WeatherVO|null` 与 Controller `Result<WeatherVO>` 一致。
