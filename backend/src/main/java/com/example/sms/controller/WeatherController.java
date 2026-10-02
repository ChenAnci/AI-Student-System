// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、天气服务接口、天气 VO、Swagger 注解与 Spring MVC 注解 =====
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
// Swagger 注解：把本控制器在文档中归入"实时天气"分组
@Api(tags = "实时天气")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/weather 开头
@RequestMapping("/api/weather")
// 实时天气控制器：提供上海实时天气查询，业务逻辑委托给 WeatherService
public class WeatherController {

    // 自动注入天气服务：由 Spring 容器装配 WeatherService 的实现类
    @Autowired
    private WeatherService weatherService;

    // Swagger 接口说明：获取上海实时天气
    @ApiOperation("获取上海实时天气")
    // GET 映射：对 /api/weather 发起 GET 即查询实时天气
    @GetMapping
    // 实时天气接口：无需参数，返回统一 Result 包裹的天气数据（异常时 data 为 null）
    public Result<WeatherVO> now() {
        // 调用服务层获取上海实时天气并封装为成功响应
        return Result.success(weatherService.getNow());
    }
}
