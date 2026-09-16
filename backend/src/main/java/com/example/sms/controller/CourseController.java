package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.dto.CourseFormDTO;
import com.example.sms.entity.Course;
import com.example.sms.service.CourseService;
import com.example.sms.vo.MyCourseVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 课程管理接口
 */
@Api(tags = "课程管理")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @ApiOperation("教师创建课程")
    @PostMapping
    public Result<Course> create(@Valid @RequestBody CourseFormDTO dto) {
        // 创建课程：教师/教秘均可用，新建课程默认 UNPUBLISHED
        return Result.success(courseService.createCourse(dto));
    }

    @ApiOperation("编辑课程（仅未发布）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CourseFormDTO dto) {
        // 编辑课程：未发布可全量编辑；已发布仅允许调课（时间/地点）
        courseService.updateCourse(id, dto);
        return Result.success();
    }

    @ApiOperation("发布课程（永久锁定）")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        // 发布课程：UNPUBLISHED -> PUBLISHED，之后课程信息锁定，学生可见可选
        courseService.publishCourse(id);
        return Result.success();
    }

    @ApiOperation("删除课程（仅未发布）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 删除课程：仅未发布且无人选课的课程可删
        courseService.deleteCourse(id);
        return Result.success();
    }

    @ApiOperation("教师：我的课程")
    @GetMapping("/my")
    public Result<List<MyCourseVO>> myCourses() {
        // 教师端"我的课程"（仅当前登录教师名下的课程）
        return Result.success(courseService.listMyCourses());
    }

    @ApiOperation("教秘：全部课程")
    @GetMapping("/all")
    public Result<List<MyCourseVO>> allCourses(@RequestParam(required = false) String keyword) {
        // 教秘端全部课程列表，支持按课程名/课程代码搜索
        return Result.success(courseService.listAllCourses(keyword));
    }
}
