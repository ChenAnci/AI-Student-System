package com.example.sms.controller;

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
@Api(tags = "成绩管理")
@RestController
@RequestMapping("/api/grades")
public class GradeController {

    @Autowired
    private GradeService gradeService;

    @ApiOperation("教师：课程选课名单（含成绩）")
    @GetMapping("/course/{courseId}/students")
    public Result<List<Map<String, Object>>> courseStudents(@PathVariable Long courseId) {
        return Result.success(gradeService.listCourseStudents(courseId));
    }

    @ApiOperation("教师：录入成绩")
    @PostMapping("/entry")
    public Result<Void> entry(@Valid @RequestBody GradeEntryDTO dto) {
        // 教师录入/修改成绩：仅 DRAFT 阶段允许，提交后锁定
        gradeService.entryGrades(dto);
        return Result.success();
    }

    @ApiOperation("教师：提交成绩（锁定）")
    @PostMapping("/{courseId}/submit")
    public Result<Void> submit(@PathVariable Long courseId) {
        // 教师提交成绩：DRAFT -> SUBMITTED，进入教秘审核队列
        gradeService.submitGrades(courseId);
        return Result.success();
    }

    @ApiOperation("教师：我的成绩流程")
    @GetMapping("/my-audits")
    public Result<List<AuditVO>> myAudits() {
        return Result.success(gradeService.listMyAudits());
    }

    @ApiOperation("教秘：待审核列表")
    @GetMapping("/pending")
    public Result<List<AuditVO>> pending() {
        return Result.success(gradeService.listPendingAudits());
    }

    @ApiOperation("教秘：全部成绩流程")
    @GetMapping("/audits")
    public Result<List<AuditVO>> audits() {
        return Result.success(gradeService.listAllAudits());
    }

    @ApiOperation("教秘：审核通过/退回")
    @PostMapping("/audit")
    public Result<Void> audit(@Valid @RequestBody AuditDTO dto) {
        // 教秘审核：通过 -> APPROVED 待发布；退回 -> DRAFT 并附原因，教师可修改后重新提交
        gradeService.audit(dto);
        return Result.success();
    }

    @ApiOperation("教秘：发布成绩（核心事务）")
    @PostMapping("/{courseId}/publish")
    public Result<Void> publish(@PathVariable Long courseId) {
        // 教秘发布成绩：APPROVED -> PUBLISHED，触发学分/GPA 重算并通知学生（核心事务）
        gradeService.publish(courseId);
        return Result.success();
    }

    @ApiOperation("学生：我的成绩单")
    @GetMapping("/my")
    public Result<List<GradeVO>> myGrades() {
        return Result.success(gradeService.myGrades(UserContext.getUserId()));
    }

    @ApiOperation("学生：学业仪表盘")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(gradeService.dashboard(UserContext.getUserId()));
    }

    @ApiOperation("教师：导出课程成绩名单 Excel")
    @GetMapping("/course/{courseId}/export")
    public void exportCourseStudents(@PathVariable Long courseId, HttpServletResponse response) {
        gradeService.exportCourseStudents(courseId, response);
    }

    @ApiOperation("教师：下载成绩导入模板（预填选课学生）")
    @GetMapping("/course/{courseId}/template")
    public void downloadGradeTemplate(@PathVariable Long courseId, HttpServletResponse response) {
        gradeService.downloadGradeTemplate(courseId, response);
    }

    @ApiOperation("教师：批量导入成绩（返回成功条数）")
    @PostMapping("/course/{courseId}/import")
    public Result<Integer> importGrades(@PathVariable Long courseId, @RequestParam("file") MultipartFile file) {
        // 教师批量导入成绩：按学号匹配本课程选课学生，返回成功导入条数
        return Result.success(gradeService.importGrades(courseId, file));
    }
}
