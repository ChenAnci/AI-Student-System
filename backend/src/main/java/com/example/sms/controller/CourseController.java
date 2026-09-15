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
        return Result.success(courseService.createCourse(dto));
    }

    @ApiOperation("编辑课程（仅未发布）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CourseFormDTO dto) {
        courseService.updateCourse(id, dto);
        return Result.success();
    }

    @ApiOperation("发布课程（永久锁定）")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        courseService.publishCourse(id);
        return Result.success();
    }

    @ApiOperation("删除课程（仅未发布）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return Result.success();
    }

    @ApiOperation("教师：我的课程")
    @GetMapping("/my")
    public Result<List<MyCourseVO>> myCourses() {
        return Result.success(courseService.listMyCourses());
    }

    @ApiOperation("教秘：全部课程")
    @GetMapping("/all")
    public Result<List<MyCourseVO>> allCourses(@RequestParam(required = false) String keyword) {
        return Result.success(courseService.listAllCourses(keyword));
    }
}
