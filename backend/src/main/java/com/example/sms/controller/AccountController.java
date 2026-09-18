package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.dto.AccountCreateDTO;
import com.example.sms.dto.AccountUpdateDTO;
import com.example.sms.dto.ChangePasswordDTO;
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

    /** 教职工列表（教秘）：keyword 可选（姓名/工号模糊搜索），返回教职工列表 */
    @ApiOperation("教职工列表")
    @GetMapping("/staffs")
    public Result<List<Staff>> listStaffs(@RequestParam(required = false) String keyword) {
        return Result.success(accountService.listStaffs(keyword));
    }

    /** 学生列表（教秘）：keyword 可选（姓名/学号模糊搜索），返回学生列表 */
    @ApiOperation("学生列表")
    @GetMapping("/students")
    public Result<List<Student>> listStudents(@RequestParam(required = false) String keyword) {
        return Result.success(accountService.listStudents(keyword));
    }

    /** 添加账号（教秘）：入参由 @Valid 校验，新增学生/教职工账号（默认密码 123456） */
    @ApiOperation("添加账号")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AccountCreateDTO dto) {
        accountService.createAccount(dto);
        return Result.success();
    }

    /** 重置密码为 123456（教秘）：按 userType（STAFF|STUDENT）与 id 定位账号 */
    @ApiOperation("重置密码为 123456")
    @PutMapping("/{userType}/{id}/password")
    public Result<Void> resetPassword(@PathVariable String userType, @PathVariable Long id) {
        accountService.resetPassword(userType, id);
        return Result.success();
    }

    /** 编辑账号信息（教秘）：可改姓名/院系/专业等，学号工号与角色不可改 */
    @ApiOperation("编辑账号信息（姓名/院系/专业等；学号工号与角色不可改）")
    @PutMapping("/{userType}/{id}")
    public Result<Void> update(@PathVariable String userType, @PathVariable Long id,
                               @Valid @RequestBody AccountUpdateDTO dto) {
        accountService.updateAccount(userType, id, dto);
        return Result.success();
    }

    /** 冻结/启用账号（教秘）：userType=STAFF|STUDENT，status=ENABLED|FROZEN|SUSPENDED */
    @ApiOperation("冻结/启用账号（userType=STAFF|STUDENT，status=ENABLED|FROZEN|SUSPENDED）")
    @PutMapping("/{userType}/{id}/status")
    public Result<Void> toggleStatus(@PathVariable String userType, @PathVariable Long id,
                                     @RequestParam String status) {
        accountService.toggleStatus(userType, id, status);
        return Result.success();
    }

    /** 当前用户信息：按登录上下文返回当前用户（学生/教师/管理员）详情 */
    @ApiOperation("当前用户信息")
    @GetMapping("/me")
    public Result<Object> myInfo() {
        return Result.success(accountService.myInfo());
    }

    /** 修改本人密码（学生/教师/管理员通用）：校验旧密码后更新为新密码 */
    @ApiOperation("修改本人密码（学生/教师/管理员通用）")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        accountService.changePassword(dto.getOldPassword(), dto.getNewPassword());
        return Result.success();
    }

    /** 导出学生列表 Excel（教秘）：直接写入 response 输出流 */
    @ApiOperation("导出学生列表 Excel")
    @GetMapping("/students/export")
    public void exportStudents(HttpServletResponse response) {
        accountService.exportStudents(response);
    }

    /** 下载学生导入模板 Excel（教秘）：供批量导入学生使用 */
    @ApiOperation("下载学生导入模板")
    @GetMapping("/students/template")
    public void downloadStudentTemplate(HttpServletResponse response) {
        accountService.downloadStudentTemplate(response);
    }

    /** 批量导入学生（教秘）：解析上传 Excel 创建账号，返回每人随机初始密码 */
    @ApiOperation("批量导入学生（返回每人随机初始密码）")
    @PostMapping("/students/import")
    public Result<List<AccountImportResultVO>> importStudents(@RequestParam("file") MultipartFile file) {
        return Result.success(accountService.importStudents(file));
    }

    /** 导出教职工列表 Excel（教秘）：直接写入 response 输出流 */
    @ApiOperation("导出教职工列表 Excel")
    @GetMapping("/staffs/export")
    public void exportStaffs(HttpServletResponse response) {
        accountService.exportStaffs(response);
    }

    /** 下载教职工导入模板 Excel（教秘）：供批量导入教职工使用 */
    @ApiOperation("下载教职工导入模板")
    @GetMapping("/staffs/template")
    public void downloadStaffTemplate(HttpServletResponse response) {
        accountService.downloadStaffTemplate(response);
    }

    /** 批量导入教职工（教秘）：仅允许导入 TEACHER，返回每人随机初始密码 */
    @ApiOperation("批量导入教职工（返回每人随机初始密码；仅允许导入 TEACHER）")
    @PostMapping("/staffs/import")
    public Result<List<AccountImportResultVO>> importStaffs(@RequestParam("file") MultipartFile file) {
        return Result.success(accountService.importStaffs(file));
    }
}
