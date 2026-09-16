package com.example.sms.controller;

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
@Api(tags = "选课管理")
@RestController
@RequestMapping("/api/enrollments")
public class EnrollController {

    @Autowired
    private EnrollService enrollService;

    @Autowired
    private CourseService courseService;

    @ApiOperation("学生选课中心（已发布课程，支持关键词/学分范围筛选）")
    @GetMapping("/center")
    public Result<List<CourseCardVO>> center(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minCredit,
            @RequestParam(required = false) BigDecimal maxCredit) {
        // 输入校验：关键词限长（防超长 %..% 模糊扫描 DoS），学分范围合法
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.length() > 50) {
                throw new BusinessException("搜索关键词过长（最多 50 字符）");
            }
        }
        if (minCredit != null && minCredit.signum() < 0) {
            throw new BusinessException("最低学分不能为负数");
        }
        if (maxCredit != null && maxCredit.signum() < 0) {
            throw new BusinessException("最高学分不能为负数");
        }
        if (minCredit != null && maxCredit != null && minCredit.compareTo(maxCredit) > 0) {
            throw new BusinessException("最低学分不能大于最高学分");
        }
        // 选课中心：学生身份取自当前登录用户（不可指定他人），已选课程用于前端标记 enrolled 状态
        Long studentId = UserContext.getUserId();
        return Result.success(courseService.listPublishedForStudent(studentId,
                enrollService.enrolledCourseIds(studentId), keyword, minCredit, maxCredit));
    }

    @ApiOperation("选课")
    @PostMapping("/{courseId}")
    public Result<Void> enroll(@PathVariable Long courseId) {
        // 学生选课：操作对象是"当前登录学生 + 指定课程"，容量/冲突等校验在 Service 层事务内完成
        enrollService.enroll(UserContext.getUserId(), courseId);
        return Result.success();
    }

    @ApiOperation("退课")
    @DeleteMapping("/{courseId}")
    public Result<Void> drop(@PathVariable Long courseId) {
        // 学生退课：同样以当前登录学生为准，成绩已发布的课程不可退
        enrollService.drop(UserContext.getUserId(), courseId);
        return Result.success();
    }

    @ApiOperation("学生：我的课表")
    @GetMapping("/my")
    public Result<List<CourseCardVO>> myCourses() {
        // 学生"我的课表"：返回本人已选课程列表（含教师名、是否满员）
        return Result.success(enrollService.myCourses(UserContext.getUserId()));
    }

    @ApiOperation("教秘：选课监控")
    @GetMapping("/monitor")
    public Result<List<EnrollMonitorVO>> monitor() {
        return Result.success(enrollService.monitor());
    }

    @ApiOperation("教秘：手动退课（studentId + courseId）")
    @DeleteMapping("/{courseId}/students/{studentId}")
    public Result<Void> adminDrop(@PathVariable Long courseId, @PathVariable Long studentId) {
        // 教秘手动退课：显式指定 studentId（教秘代操作），Service 层校验 ADMIN 角色
        enrollService.adminDrop(studentId, courseId);
        return Result.success();
    }

    @ApiOperation("教秘：代学生选课（studentId + courseId）")
    @PostMapping("/{courseId}/students/{studentId}")
    public Result<Void> adminEnroll(@PathVariable Long courseId, @PathVariable Long studentId) {
        // 教秘代学生选课：显式指定 studentId，复用学生选课全量校验与通知
        enrollService.adminEnroll(studentId, courseId);
        return Result.success();
    }
}
