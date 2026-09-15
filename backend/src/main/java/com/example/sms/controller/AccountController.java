package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.dto.AccountCreateDTO;
import com.example.sms.dto.AccountUpdateDTO;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.service.AccountService;
import com.example.sms.vo.AccountImportResultVO;
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
 * 账号管理接口（教学秘书）
 */
@Api(tags = "账号管理")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @ApiOperation("教职工列表")
    @GetMapping("/staffs")
    public Result<List<Staff>> listStaffs(@RequestParam(required = false) String keyword) {
        return Result.success(accountService.listStaffs(keyword));
    }

    @ApiOperation("学生列表")
    @GetMapping("/students")
    public Result<List<Student>> listStudents(@RequestParam(required = false) String keyword) {
        return Result.success(accountService.listStudents(keyword));
    }

    @ApiOperation("添加账号")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AccountCreateDTO dto) {
        accountService.createAccount(dto);
        return Result.success();
    }

    @ApiOperation("重置密码为 123456")
    @PutMapping("/{userType}/{id}/password")
    public Result<Void> resetPassword(@PathVariable String userType, @PathVariable Long id) {
        accountService.resetPassword(userType, id);
        return Result.success();
    }

    @ApiOperation("编辑账号信息（姓名/院系/专业等；学号工号与角色不可改）")
    @PutMapping("/{userType}/{id}")
    public Result<Void> update(@PathVariable String userType, @PathVariable Long id,
                               @Valid @RequestBody AccountUpdateDTO dto) {
        accountService.updateAccount(userType, id, dto);
        return Result.success();
    }

    @ApiOperation("冻结/启用账号（userType=STAFF|STUDENT，status=ENABLED|FROZEN|SUSPENDED）")
    @PutMapping("/{userType}/{id}/status")
    public Result<Void> toggleStatus(@PathVariable String userType, @PathVariable Long id,
                                     @RequestParam String status) {
        accountService.toggleStatus(userType, id, status);
        return Result.success();
    }

    @ApiOperation("当前用户信息")
    @GetMapping("/me")
    public Result<Object> myInfo() {
        return Result.success(accountService.myInfo());
    }

    @ApiOperation("导出学生列表 Excel")
    @GetMapping("/students/export")
    public void exportStudents(HttpServletResponse response) {
        accountService.exportStudents(response);
    }

    @ApiOperation("下载学生导入模板")
    @GetMapping("/students/template")
    public void downloadStudentTemplate(HttpServletResponse response) {
        accountService.downloadStudentTemplate(response);
    }

    @ApiOperation("批量导入学生（返回每人随机初始密码）")
    @PostMapping("/students/import")
    public Result<List<AccountImportResultVO>> importStudents(@RequestParam("file") MultipartFile file) {
        return Result.success(accountService.importStudents(file));
    }

    @ApiOperation("导出教职工列表 Excel")
    @GetMapping("/staffs/export")
    public void exportStaffs(HttpServletResponse response) {
        accountService.exportStaffs(response);
    }

    @ApiOperation("下载教职工导入模板")
    @GetMapping("/staffs/template")
    public void downloadStaffTemplate(HttpServletResponse response) {
        accountService.downloadStaffTemplate(response);
    }

    @ApiOperation("批量导入教职工（返回每人随机初始密码；仅允许导入 TEACHER）")
    @PostMapping("/staffs/import")
    public Result<List<AccountImportResultVO>> importStaffs(@RequestParam("file") MultipartFile file) {
        return Result.success(accountService.importStaffs(file));
    }
}
