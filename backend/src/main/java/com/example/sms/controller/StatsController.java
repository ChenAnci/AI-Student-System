package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.service.StatsService;
import com.example.sms.util.UserContext;
import com.example.sms.vo.AdminStatsVO;
import com.example.sms.vo.TeacherStatsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计接口
 */
@Api(tags = "数据统计")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @ApiOperation("教秘端全校数据统计")
    @GetMapping("/admin")
    public Result<AdminStatsVO> adminStats() {
        return Result.success(statsService.adminStats());
    }

    @ApiOperation("教师端所授课程成绩统计")
    @GetMapping("/teacher")
    public Result<TeacherStatsVO> teacherStats() {
        return Result.success(statsService.teacherStats(UserContext.getUserId()));
    }
}
