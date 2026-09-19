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
