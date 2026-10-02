// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、成绩相关 DTO/VO、成绩服务接口、用户上下文工具、Swagger 注解、Spring MVC 注解、文件上传、Servlet 导出与校验、集合工具类 =====
import com.example.sms.common.Result;
import com.example.sms.dto.AuditDTO;
import com.example.sms.dto.GradeEntryDTO;
import com.example.sms.service.GradeService;
import com.example.sms.util.UserContext;
import com.example.sms.vo.AuditVO;
import com.example.sms.vo.GradeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 成绩管理接口
 */
// Swagger 注解：把本控制器在文档中归入"成绩管理"分组
@Api(tags = "成绩管理")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/grades 开头
@RequestMapping("/api/grades")
// 成绩管理控制器：负责成绩录入/提交/审核/发布/查询/导入导出等请求，业务逻辑委托给 GradeService
public class GradeController {

    // 自动注入成绩服务：由 Spring 容器装配 GradeService 的实现类
    @Autowired
    private GradeService gradeService;

    /** 课程选课名单（教师）：按课程 id 返回选课学生列表（含成绩字段） */
    // Swagger 接口说明：教师：课程选课名单（含成绩）
    @ApiOperation("教师：课程选课名单（含成绩）")
    // GET 映射：路径模板 /course/{courseId}/students，按课程查询选课学生
    @GetMapping("/course/{courseId}/students")
    // 课程选课名单：@PathVariable 绑定课程 id，返回选课学生列表（含成绩字段）
    public Result<List<Map<String, Object>>> courseStudents(@PathVariable Long courseId) {
        // 调用服务层返回该课程的选课学生名单
        return Result.success(gradeService.listCourseStudents(courseId));
    }

    // Swagger 接口说明：教师：录入成绩
    @ApiOperation("教师：录入成绩")
    // POST 映射：访问 /api/grades/entry 录入成绩
    @PostMapping("/entry")
    // 录入成绩：@Valid 校验成绩条目，@RequestBody 绑定 GradeEntryDTO
    public Result<Void> entry(@Valid @RequestBody GradeEntryDTO dto) {
        // 教师录入/修改成绩：仅 DRAFT 阶段允许，提交后锁定
        // 调用服务层录入或修改成绩
        gradeService.entryGrades(dto);
        // 录入成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：教师：提交成绩（锁定）
    @ApiOperation("教师：提交成绩（锁定）")
    // POST 映射：路径模板 /{courseId}/submit，提交某课程成绩
    @PostMapping("/{courseId}/submit")
    // 提交成绩：@PathVariable 绑定课程 id
    public Result<Void> submit(@PathVariable Long courseId) {
        // 教师提交成绩：DRAFT -> SUBMITTED，进入教秘审核队列
        // 调用服务层提交该课程的成绩
        gradeService.submitGrades(courseId);
        // 提交成功返回成功响应
        return Result.success();
    }

    /** 我的成绩流程（教师）：返回当前教师发起的成绩审核记录列表 */
    // Swagger 接口说明：教师：我的成绩流程
    @ApiOperation("教师：我的成绩流程")
    // GET 映射：访问 /api/grades/my-audits 查询当前教师发起的成绩流程
    @GetMapping("/my-audits")
    // 我的成绩流程：无需参数，身份取自登录上下文
    public Result<List<AuditVO>> myAudits() {
        // 调用服务层返回当前教师的成绩审核记录列表
        return Result.success(gradeService.listMyAudits());
    }

    /** 待审核列表（教秘）：返回处于已提交（SUBMITTED）待审核状态的成绩流程 */
    // Swagger 接口说明：教秘：待审核列表
    @ApiOperation("教秘：待审核列表")
    // GET 映射：访问 /api/grades/pending 查询待审核流程
    @GetMapping("/pending")
    // 待审核列表：无需参数，返回所有待审核的成绩流程
    public Result<List<AuditVO>> pending() {
        // 调用服务层返回待审核（SUBMITTED 状态）的成绩流程列表
        return Result.success(gradeService.listPendingAudits());
    }

    /** 全部成绩流程（教秘）：返回所有成绩审核记录列表 */
    // Swagger 接口说明：教秘：全部成绩流程
    @ApiOperation("教秘：全部成绩流程")
    // GET 映射：访问 /api/grades/audits 查询全部成绩流程
    @GetMapping("/audits")
    // 全部成绩流程：无需参数，返回所有审核记录
    public Result<List<AuditVO>> audits() {
        // 调用服务层返回全部成绩审核记录列表
        return Result.success(gradeService.listAllAudits());
    }

    // Swagger 接口说明：教秘：审核通过/退回
    @ApiOperation("教秘：审核通过/退回")
    // POST 映射：访问 /api/grades/audit 提交审核结论
    @PostMapping("/audit")
    // 教秘审核：@Valid 校验审核结论，@RequestBody 绑定 AuditDTO
    public Result<Void> audit(@Valid @RequestBody AuditDTO dto) {
        // 教秘审核：通过 -> APPROVED 待发布；退回 -> DRAFT 并附原因，教师可修改后重新提交
        // 调用服务层执行审核操作
        gradeService.audit(dto);
        // 审核成功返回成功响应
        return Result.success();
    }

    // Swagger 接口说明：教秘：发布成绩（核心事务）
    @ApiOperation("教秘：发布成绩（核心事务）")
    // POST 映射：路径模板 /{courseId}/publish，发布某课程成绩
    @PostMapping("/{courseId}/publish")
    // 发布成绩：@PathVariable 绑定课程 id
    public Result<Void> publish(@PathVariable Long courseId) {
        // 教秘发布成绩：APPROVED -> PUBLISHED，触发学分/GPA 重算并通知学生（核心事务）
        // 调用服务层发布该课程成绩（核心事务：重算学分 GPA 并通知学生）
        gradeService.publish(courseId);
        // 发布成功返回成功响应
        return Result.success();
    }

    /** 我的成绩单（学生）：返回当前学生的已发布（PUBLISHED）成绩列表 */
    // Swagger 接口说明：学生：我的成绩单
    @ApiOperation("学生：我的成绩单")
    // GET 映射：访问 /api/grades/my 查询当前学生成绩单
    @GetMapping("/my")
    // 我的成绩单：无需参数，学生身份取自登录上下文
    public Result<List<GradeVO>> myGrades() {
        // 调用服务层返回当前学生的已发布成绩列表
        return Result.success(gradeService.myGrades(UserContext.getUserId()));
    }

    /** 学业仪表盘（学生）：返回当前学生的成绩汇总统计（GPA/学分等） */
    // Swagger 接口说明：学生：学业仪表盘
    @ApiOperation("学生：学业仪表盘")
    // GET 映射：访问 /api/grades/dashboard 查询学业统计
    @GetMapping("/dashboard")
    // 学业仪表盘：无需参数，学生身份取自登录上下文
    public Result<Map<String, Object>> dashboard() {
        // 调用服务层返回当前学生的成绩汇总统计（GPA/学分等）
        return Result.success(gradeService.dashboard(UserContext.getUserId()));
    }

    /** 导出课程成绩名单 Excel（教师）：直接写入 response 输出流 */
    // Swagger 接口说明：教师：导出课程成绩名单 Excel
    @ApiOperation("教师：导出课程成绩名单 Excel")
    // GET 映射：路径模板 /course/{courseId}/export，导出某课程成绩名单
    @GetMapping("/course/{courseId}/export")
    // 导出课程成绩名单：@PathVariable 绑定课程 id，Excel 直接写 response 输出流
    public void exportCourseStudents(@PathVariable Long courseId, HttpServletResponse response) {
        // 调用服务层生成成绩名单 Excel 并写入响应流
        gradeService.exportCourseStudents(courseId, response);
    }

    /** 下载成绩导入模板（教师）：模板预填本课程选课学生，供批量导入使用 */
    // Swagger 接口说明：教师：下载成绩导入模板（预填选课学生）
    @ApiOperation("教师：下载成绩导入模板（预填选课学生）")
    // GET 映射：路径模板 /course/{courseId}/template，下载某课程成绩导入模板
    @GetMapping("/course/{courseId}/template")
    // 下载成绩导入模板：@PathVariable 绑定课程 id，模板预填该课程选课学生
    public void downloadGradeTemplate(@PathVariable Long courseId, HttpServletResponse response) {
        // 调用服务层生成预填选课学生的成绩导入模板并写入响应流
        gradeService.downloadGradeTemplate(courseId, response);
    }

    // Swagger 接口说明：教师：批量导入成绩（返回成功条数）
    @ApiOperation("教师：批量导入成绩（返回成功条数）")
    // POST 映射：路径模板 /course/{courseId}/import，上传 Excel 批量导入成绩
    @PostMapping("/course/{courseId}/import")
    // 批量导入成绩：@PathVariable 绑定课程 id，@RequestParam 接收上传文件（字段名 file）
    public Result<Integer> importGrades(@PathVariable Long courseId, @RequestParam("file") MultipartFile file) {
        // 教师批量导入成绩：按学号匹配本课程选课学生，返回成功导入条数
        // 调用服务层解析 Excel 导入成绩，返回成功条数
        return Result.success(gradeService.importGrades(courseId, file));
    }
}
