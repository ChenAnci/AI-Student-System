package com.example.sms.controller;

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
        Long studentId = UserContext.getUserId();
        return Result.success(courseService.listPublishedForStudent(studentId,
                enrollService.enrolledCourseIds(studentId), keyword, minCredit, maxCredit));
    }

    @ApiOperation("选课")
    @PostMapping("/{courseId}")
    public Result<Void> enroll(@PathVariable Long courseId) {
        enrollService.enroll(UserContext.getUserId(), courseId);
        return Result.success();
    }

    @ApiOperation("退课")
    @DeleteMapping("/{courseId}")
    public Result<Void> drop(@PathVariable Long courseId) {
        enrollService.drop(UserContext.getUserId(), courseId);
        return Result.success();
    }

    @ApiOperation("学生：我的课表")
    @GetMapping("/my")
    public Result<List<CourseCardVO>> myCourses() {
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
        enrollService.adminDrop(studentId, courseId);
        return Result.success();
    }

    @ApiOperation("教秘：代学生选课（studentId + courseId）")
    @PostMapping("/{courseId}/students/{studentId}")
    public Result<Void> adminEnroll(@PathVariable Long courseId, @PathVariable Long studentId) {
        enrollService.adminEnroll(studentId, courseId);
        return Result.success();
    }
}
