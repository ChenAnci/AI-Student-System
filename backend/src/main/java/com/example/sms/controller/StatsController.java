// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、统计服务接口、用户上下文工具、统计 VO、Swagger 注解与 Spring MVC 注解 =====
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
// Swagger 注解：把本控制器在文档中归入"数据统计"分组
@Api(tags = "数据统计")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/stats 开头
@RequestMapping("/api/stats")
// 数据统计控制器：提供教秘端全校统计与教师端所授课程统计，业务逻辑委托给 StatsService
public class StatsController {

    // 自动注入统计服务：由 Spring 容器装配 StatsService 的实现类
    @Autowired
    private StatsService statsService;

    /** 教秘端全校数据统计：返回学生/课程/选课等全校汇总数据 */
    // Swagger 接口说明：教秘端全校数据统计
    @ApiOperation("教秘端全校数据统计")
    // GET 映射：访问 /api/stats/admin 查询全校统计
    @GetMapping("/admin")
    // 教秘端全校统计接口：无需参数，返回全校学生/课程/选课等汇总数据
    public Result<AdminStatsVO> adminStats() {
        // 调用服务层统计全校数据并封装为成功响应
        return Result.success(statsService.adminStats());
    }

    /** 教师端所授课程成绩统计：按当前登录教师统计其所授课程的成绩情况 */
    // Swagger 接口说明：教师端所授课程成绩统计
    @ApiOperation("教师端所授课程成绩统计")
    // GET 映射：访问 /api/stats/teacher 查询教师统计
    @GetMapping("/teacher")
    // 教师端统计接口：无需参数，教师身份取自登录上下文
    public Result<TeacherStatsVO> teacherStats() {
        // 调用服务层按当前登录教师统计其所授课程的成绩情况
        return Result.success(statsService.teacherStats(UserContext.getUserId()));
    }
}
