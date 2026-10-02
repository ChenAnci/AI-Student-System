// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、课程 DTO/实体/VO、课程服务接口、Swagger 注解、Spring MVC 注解、参数校验与集合工具类 =====
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
// Swagger 注解：把本控制器在文档中归入"课程管理"分组
@Api(tags = "课程管理")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/courses 开头
@RequestMapping("/api/courses")
// 课程管理控制器：负责课程增删改查的请求接收，业务逻辑委托给 CourseService
public class CourseController {

    // 自动注入课程服务：由 Spring 容器装配 CourseService 的实现类
    @Autowired
    private CourseService courseService;

    // Swagger 接口说明：教师创建课程
    @ApiOperation("教师创建课程")
    // POST 映射：对 /api/courses 发起 POST 表示创建课程
    @PostMapping
    // 创建课程：@Valid 校验表单字段，@RequestBody 绑定 CourseFormDTO，返回新建的课程实体
    public Result<Course> create(@Valid @RequestBody CourseFormDTO dto) {
        // 创建课程：教师/教秘均可用，新建课程默认 UNPUBLISHED
        // 调用服务层创建课程并返回结果
        return Result.success(courseService.createCourse(dto));
    }

    // Swagger 接口说明：编辑课程（仅未发布）
    @ApiOperation("编辑课程（仅未发布）")
    // PUT 映射：路径模板 /{id}，按课程 id 更新
    @PutMapping("/{id}")
    // 编辑课程：@PathVariable 绑定路径中的课程 id，@Valid 校验更新内容
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CourseFormDTO dto) {
        // 编辑课程：未发布可全量编辑；已发布仅允许调课（时间/地点）
        // 调用服务层更新课程
        courseService.updateCourse(id, dto);
        // 更新成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：发布课程（永久锁定）
    @ApiOperation("发布课程（永久锁定）")
    // POST 映射：路径模板 /{id}/publish，发布指定课程
    @PostMapping("/{id}/publish")
    // 发布课程：仅需课程 id
    public Result<Void> publish(@PathVariable Long id) {
        // 发布课程：UNPUBLISHED -> PUBLISHED，之后课程信息锁定，学生可见可选
        // 调用服务层发布课程
        courseService.publishCourse(id);
        // 发布成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：删除课程（仅未发布）
    @ApiOperation("删除课程（仅未发布）")
    // DELETE 映射：路径模板 /{id}，删除指定课程
    @DeleteMapping("/{id}")
    // 删除课程：仅需课程 id
    public Result<Void> delete(@PathVariable Long id) {
        // 删除课程：仅未发布且无人选课的课程可删
        // 调用服务层删除课程
        courseService.deleteCourse(id);
        // 删除成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：教师：我的课程
    @ApiOperation("教师：我的课程")
    // GET 映射：访问 /api/courses/my 查询当前教师的课程
    @GetMapping("/my")
    // 教师"我的课程"接口：无需参数，身份取自登录上下文
    public Result<List<MyCourseVO>> myCourses() {
        // 教师端"我的课程"（仅当前登录教师名下的课程）
        // 调用服务层返回当前教师名下课程列表
        return Result.success(courseService.listMyCourses());
    }

    // Swagger 接口说明：教秘：全部课程
    @ApiOperation("教秘：全部课程")
    // GET 映射：访问 /api/courses/all 查询全部课程
    @GetMapping("/all")
    // 教秘全部课程接口：keyword 可选（按课程名/课程代码搜索）
    public Result<List<MyCourseVO>> allCourses(@RequestParam(required = false) String keyword) {
        // 教秘端全部课程列表，支持按课程名/课程代码搜索
        // 调用服务层返回全部课程列表
        return Result.success(courseService.listAllCourses(keyword));
    }
}
