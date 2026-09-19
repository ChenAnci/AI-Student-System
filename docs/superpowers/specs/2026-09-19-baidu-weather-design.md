# 百度实时天气卡片 · 设计文档

日期：2026-09-19
状态：已批准（用户确认"执行吧"）

## 1. 背景与目标

在系统的三个角色页面（学生仪表盘、教师统计页、教秘统计页）接入**实时天气卡片**，展示上海市当前天气。

- 数据源：百度地图开放平台「天气查询」接口（`api.map.baidu.com/weather/v1`）
- AK：`ql0irM7pCYFqcyqzp0j1nDYpnqrN5518`（由用户提供，仅后端环境变量注入）
- 位置：上海市（`district_id=310100`）
- 显示内容：城市 / 天气现象 / 温度 / 体感温度 / 风力风向 / 湿度 / 更新时间

## 2. 方案选型

**方案 A（选定）：后端代理 + 内存 TTL 缓存**
- 后端新增 `WeatherController /api/weather`（需登录，三角色均可访问），内部调用百度接口
- AK 通过 `BAIDU_WEATHER_AK` 环境变量注入，**不写入仓库、不暴露到前端**
- 响应做 30 分钟 TTL 内存缓存，保护个人开发者免费配额（默认约 1000 次/天）
- 前端抽通用 `WeatherCard.vue` 组件，三页面复用；接口失败仅卡片内降级提示，不影响页面其它数据

弃用方案 B（前端直连：AK 暴露浏览器、有 CORS 与配额盗用风险）、方案 C（后端无缓存：烧配额、慢）。

## 3. 架构与数据流

```
前端三页面 ──GET /api/weather──> WeatherController ──> WeatherService
                                                          │ 30min TTL 内存缓存
                                                          ▼
                              https://api.map.baidu.com/weather/v1/?district_id=310100&data_type=now&ak=xxx
```

## 4. 后端设计

### 4.1 配置（application.yml）

```yaml
baidu:
  weather:
    ak: ${BAIDU_WEATHER_AK:}        # 百度地图 AK，环境变量注入，缺省为空
    district-id: 310100             # 上海市
    cache-ttl-seconds: 1800         # 缓存 30 分钟
```

### 4.2 WeatherController

- `GET /api/weather`（需登录，`JwtInterceptor` 允许任意已登录角色）
- 返回 `Result<WeatherVO>`，统一信封

### 4.3 WeatherService

- 请求百度接口 `https://api.map.baidu.com/weather/v1/`，参数 `district_id / data_type=now / ak`
- 解析 `result.location`（城市名）与 `result.now`（text 天气现象、temp 温度、feels_like 体感、wind_class 风力等级、wind_dir 风向、rh 湿度、uptime 更新时间）
- **缓存**：`ConcurrentHashMap<String, WeatherVO>` + 写入时间戳；命中且未过期直接返回
- **降级**：AK 未配置 → 返回空（code 200 + data=null 或提示）；百度接口失败/超时（HTTP 超时 5s）→ 返回空 + WARN 日志，不抛 500
- 校验：`status==0` 才算成功；非 0 记录 message 并降级

### 4.4 安全

- AK 仅存在于进程环境变量（`BAIDU_WEATHER_AK`），不写入任何提交文件，前端不感知
- 天气接口本身无用户数据，无额外鉴权细节要求

## 5. 前端设计

### 5.1 WeatherCard.vue（新增通用组件）

- 展示：城市 / 天气现象 / 温度 / 体感 / 风力风向 / 湿度 / 更新时间
- 加载中显示骨架；失败显示"天气服务暂不可用"（不阻塞页面）
- 天气现象配简单 emoji 映射（晴/多云/阴/雨/雪/雾…），不引入图片资源

### 5.2 api/weather.ts

- `getWeather()` 封装 `GET /api/weather`

### 5.3 接入位置

- 学生仪表盘 `views/student/DashboardView.vue`
- 教师统计页 `views/teacher/TeacherStatsView.vue`
- 教秘统计页 `views/admin/StatsView.vue`

## 6. 测试

- `WeatherServiceTest`：mock 百度响应解析正确；缓存命中不重复请求；AK 缺失降级返回空；百度异常降级不抛异常
- 前端 `npx vue-tsc --noEmit` 类型检查通过
- 手工冒烟：三页面卡片正常显示；停掉百度返回时卡片降级

## 7. 不做（YAGNI）

- 多城市切换 / 地理定位
- 未来天气预报（`data_type=forecast`）
- 历史天气、图表展示
- 前端展示图片型天气图标
