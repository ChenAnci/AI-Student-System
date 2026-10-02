// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：业务异常、统一响应体 Result、课程/选课服务接口、用户上下文工具、选课相关 VO、Swagger 注解、Spring MVC 注解与数值、集合工具类 =====
import com.example.sms.common.BusinessException;
import com.example.sms.common.Result;
import com.example.sms.service.CourseService;
import com.example.sms.service.EnrollService;
import com.example.sms.util.UserContext;
import com.example.sms.vo.CourseCardVO;
import com.example.sms.vo.EnrollMonitorVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 选课/退课接口
 */
// Swagger 注解：把本控制器在文档中归入"选课管理"分组
@Api(tags = "选课管理")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/enrollments 开头
@RequestMapping("/api/enrollments")
// 选课管理控制器：负责学生选课/退课与教秘代操作、监控等请求，业务逻辑委托给 EnrollService/CourseService
public class EnrollController {

    // 自动注入选课服务：负责选课、退课、课表、监控等核心业务
    @Autowired
    private EnrollService enrollService;

    // 自动注入课程服务：负责课程中心列表查询（已发布课程等）
    @Autowired
    private CourseService courseService;

    // Swagger 接口说明：学生选课中心（已发布课程，支持关键词/学分范围筛选）
    @ApiOperation("学生选课中心（已发布课程，支持关键词/学分范围筛选）")
    // GET 映射：访问 /api/enrollments/center 进入选课中心
    @GetMapping("/center")
    // 选课中心接口：keyword（关键词）、minCredit（最低学分）、maxCredit（最高学分）三个筛选参数均可选
    public Result<List<CourseCardVO>> center(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minCredit,
            @RequestParam(required = false) BigDecimal maxCredit) {
        // 输入校验：关键词限长（防超长 %..% 模糊扫描 DoS），学分范围合法
        // 关键词非空时先做校验
        if (keyword != null) {
            // 去掉关键词首尾空白，避免误带空格影响匹配
            keyword = keyword.trim();
            // 关键词超过 50 字符直接拒绝，防止超长模糊查询拖垮数据库
            if (keyword.length() > 50) {
                throw new BusinessException("搜索关键词过长（最多 50 字符）");
            }
        }
        // 最低学分为负数是非法输入，直接抛业务异常
        if (minCredit != null && minCredit.signum() < 0) {
            throw new BusinessException("最低学分不能为负数");
        }
        // 最高学分为负数是非法输入，直接抛业务异常
        if (maxCredit != null && maxCredit.signum() < 0) {
            throw new BusinessException("最高学分不能为负数");
        }
        // 最低学分大于最高学分时区间为空，属于非法组合，直接拒绝
        if (minCredit != null && maxCredit != null && minCredit.compareTo(maxCredit) > 0) {
            throw new BusinessException("最低学分不能大于最高学分");
        }
        // 选课中心：学生身份取自当前登录用户（不可指定他人），已选课程用于前端标记 enrolled 状态
        // 从登录上下文取出当前学生 id（不可由前端指定他人）
        Long studentId = UserContext.getUserId();
        // 调用服务层：查询该学生可见的已发布课程，同时带上其已选课程 id、筛选条件，返回选课中心列表
        return Result.success(courseService.listPublishedForStudent(studentId,
                enrollService.enrolledCourseIds(studentId), keyword, minCredit, maxCredit));
    }

    // Swagger 接口说明：选课
    @ApiOperation("选课")
    // POST 映射：路径模板 /{courseId}，为当前学生选指定课程
    @PostMapping("/{courseId}")
    // 学生选课：@PathVariable 绑定课程 id
    public Result<Void> enroll(@PathVariable Long courseId) {
        // 学生选课：操作对象是"当前登录学生 + 指定课程"，容量/冲突等校验在 Service 层事务内完成
        // 调用选课服务：以当前登录学生 id 与指定课程 id 完成选课
        enrollService.enroll(UserContext.getUserId(), courseId);
        // 选课成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：退课
    @ApiOperation("退课")
    // DELETE 映射：路径模板 /{courseId}，为当前学生退掉指定课程
    @DeleteMapping("/{courseId}")
    // 学生退课：@PathVariable 绑定课程 id
    public Result<Void> drop(@PathVariable Long courseId) {
        // 学生退课：同样以当前登录学生为准，成绩已发布的课程不可退
        // 调用退课服务：以当前登录学生 id 与指定课程 id 完成退课
        enrollService.drop(UserContext.getUserId(), courseId);
        // 退课成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：学生：我的课表
    @ApiOperation("学生：我的课表")
    // GET 映射：访问 /api/enrollments/my 查询当前学生课表
    @GetMapping("/my")
    // 学生"我的课表"接口：无需参数，身份取自登录上下文
    public Result<List<CourseCardVO>> myCourses() {
        // 学生"我的课表"：返回本人已选课程列表（含教师名、是否满员）
        // 调用选课服务返回当前学生已选课程列表
        return Result.success(enrollService.myCourses(UserContext.getUserId()));
    }

    // Swagger 接口说明：教秘：选课监控
    @ApiOperation("教秘：选课监控")
    // GET 映射：访问 /api/enrollments/monitor 查询选课监控数据
    @GetMapping("/monitor")
    // 教秘选课监控接口：无需参数，返回各课程选课统计
    public Result<List<EnrollMonitorVO>> monitor() {
        // 教秘选课监控：返回各课程选课人数/容量等统计，供选课监控页展示
        // 调用选课服务返回选课监控统计列表
        return Result.success(enrollService.monitor());
    }

    // Swagger 接口说明：教秘：手动退课（studentId + courseId）
    @ApiOperation("教秘：手动退课（studentId + courseId）")
    // DELETE 映射：路径模板 /{courseId}/students/{studentId}，教秘代指定学生退课
    @DeleteMapping("/{courseId}/students/{studentId}")
    // 教秘手动退课：@PathVariable 分别绑定课程 id 与学生 id
    public Result<Void> adminDrop(@PathVariable Long courseId, @PathVariable Long studentId) {
        // 教秘手动退课：显式指定 studentId（教秘代操作），Service 层校验 ADMIN 角色
        // 调用退课服务：按指定的学生与课程退课（角色校验在服务层完成）
        enrollService.adminDrop(studentId, courseId);
        // 退课成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：教秘：代学生选课（studentId + courseId）
    @ApiOperation("教秘：代学生选课（studentId + courseId）")
    // POST 映射：路径模板 /{courseId}/students/{studentId}，教秘代指定学生选课
    @PostMapping("/{courseId}/students/{studentId}")
    // 教秘代学生选课：@PathVariable 分别绑定课程 id 与学生 id
    public Result<Void> adminEnroll(@PathVariable Long courseId, @PathVariable Long studentId) {
        // 教秘代学生选课：显式指定 studentId，复用学生选课全量校验与通知
        // 调用选课服务：按指定的学生与课程完成选课
        enrollService.adminEnroll(studentId, courseId);
        // 选课成功返回成功响应
        return Result.success();
    }
}
