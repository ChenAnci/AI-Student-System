// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、账号相关 DTO/实体/VO、账号服务接口、Swagger 注解、Spring MVC 注解、文件上传与校验、Servlet 与集合工具类 =====
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
// Swagger 注解：把本控制器在文档中归入"账号管理"分组
@Api(tags = "账号管理")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/accounts 开头
@RequestMapping("/api/accounts")
// 账号管理控制器：只负责参数绑定与结果封装，具体业务逻辑委托给 AccountService 处理
public class AccountController {

    // 自动注入账号服务：由 Spring 容器装配 AccountService 的实现类（默认单例复用）
    @Autowired
    private AccountService accountService;

    /** 教职工列表（教秘）：keyword 可选（姓名/工号模糊搜索），返回教职工列表 */
    // Swagger 接口说明：教职工列表
    @ApiOperation("教职工列表")
    // GET 映射：访问 /api/accounts/staffs 时进入该方法
    @GetMapping("/staffs")
    // 处理"教职工列表"请求：keyword 为可选查询参数（姓名/工号模糊搜索），返回统一 Result 包裹的教职工列表
    public Result<List<Staff>> listStaffs(@RequestParam(required = false) String keyword) {
        // 调用服务层查询教职工列表，并封装为成功响应返回
        return Result.success(accountService.listStaffs(keyword));
    }

    /** 学生列表（教秘）：keyword 可选（姓名/学号模糊搜索），返回学生列表 */
    // Swagger 接口说明：学生列表
    @ApiOperation("学生列表")
    // GET 映射：访问 /api/accounts/students 时进入该方法
    @GetMapping("/students")
    // 处理"学生列表"请求：keyword 可选（姓名/学号模糊搜索），返回统一 Result 包裹的学生列表
    public Result<List<Student>> listStudents(@RequestParam(required = false) String keyword) {
        // 调用服务层查询学生列表，并封装为成功响应返回
        return Result.success(accountService.listStudents(keyword));
    }

    /** 添加账号（教秘）：入参由 @Valid 校验，新增学生/教职工账号（默认密码 123456） */
    // Swagger 接口说明：添加账号
    @ApiOperation("添加账号")
    // POST 映射：对 /api/accounts 发起 POST 表示新增账号
    @PostMapping
    // 新增账号：@Valid 触发 DTO 字段校验，@RequestBody 将 JSON 请求体绑定为 AccountCreateDTO
    public Result<Void> create(@Valid @RequestBody AccountCreateDTO dto) {
        // 调用服务层创建账号（新增学生/教职工账号，默认密码 123456）
        accountService.createAccount(dto);
        // 创建成功返回无数据的成功响应
        return Result.success();
    }

    /** 重置密码为 123456（教秘）：按 userType（STAFF|STUDENT）与 id 定位账号 */
    // Swagger 接口说明：重置密码为 123456
    @ApiOperation("重置密码为 123456")
    // PUT 映射：路径模板 /{userType}/{id}/password，用于重置指定账号密码
    @PutMapping("/{userType}/{id}/password")
    // 重置密码：userType 区分教职工/学生，id 为账号主键，两者拼出唯一账号
    public Result<Void> resetPassword(@PathVariable String userType, @PathVariable Long id) {
        // 调用服务层将指定账号密码重置为默认值 123456
        accountService.resetPassword(userType, id);
        // 操作成功返回成功响应
        return Result.success();
    }

    /** 编辑账号信息（教秘）：可改姓名/院系/专业等，学号工号与角色不可改 */
    // Swagger 接口说明：编辑账号信息（姓名/院系/专业等；学号工号与角色不可改）
    @ApiOperation("编辑账号信息（姓名/院系/专业等；学号工号与角色不可改）")
    // PUT 映射：路径模板 /{userType}/{id}，更新指定账号信息
    @PutMapping("/{userType}/{id}")
    // 编辑账号：按 userType+id 定位账号，@Valid 校验更新内容（学号/工号与角色不可修改）
    public Result<Void> update(@PathVariable String userType, @PathVariable Long id,
                               @Valid @RequestBody AccountUpdateDTO dto) {
        // 调用服务层更新账号信息（姓名/院系/专业等）
        accountService.updateAccount(userType, id, dto);
        // 更新成功返回成功响应
        return Result.success();
    }

    /** 冻结/启用账号（教秘）：userType=STAFF|STUDENT，status=ENABLED|FROZEN|SUSPENDED */
    // Swagger 接口说明：冻结/启用账号（userType=STAFF|STUDENT，status=ENABLED|FROZEN|SUSPENDED）
    @ApiOperation("冻结/启用账号（userType=STAFF|STUDENT，status=ENABLED|FROZEN|SUSPENDED）")
    // PUT 映射：路径模板 /{userType}/{id}/status，用于变更账号状态
    @PutMapping("/{userType}/{id}/status")
    // 冻结/启用：status 为目标状态（ENABLED 启用 / FROZEN 冻结 / SUSPENDED 停用），由查询参数传入
    public Result<Void> toggleStatus(@PathVariable String userType, @PathVariable Long id,
                                     @RequestParam String status) {
        // 调用服务层切换账号状态
        accountService.toggleStatus(userType, id, status);
        // 操作成功返回成功响应
        return Result.success();
    }

    /** 当前用户信息：按登录上下文返回当前用户（学生/教师/管理员）详情 */
    // Swagger 接口说明：当前用户信息
    @ApiOperation("当前用户信息")
    // GET 映射：访问 /api/accounts/me 返回当前登录用户信息
    @GetMapping("/me")
    // 处理"当前用户信息"请求：无需参数，从登录上下文解析当前用户身份
    public Result<Object> myInfo() {
        // 调用服务层返回当前用户详情（学生/教师/管理员）
        return Result.success(accountService.myInfo());
    }

    /** 修改本人密码（学生/教师/管理员通用）：校验旧密码后更新为新密码 */
    // Swagger 接口说明：修改本人密码（学生/教师/管理员通用）
    @ApiOperation("修改本人密码（学生/教师/管理员通用）")
    // PUT 映射：访问 /api/accounts/me/password 修改本人密码
    @PutMapping("/me/password")
    // 修改密码：@Valid 校验新旧密码非空等格式，@RequestBody 绑定 ChangePasswordDTO
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        // 调用服务层先校验旧密码，再更新为新密码
        accountService.changePassword(dto.getOldPassword(), dto.getNewPassword());
        // 修改成功返回成功响应
        return Result.success();
    }

    /** 导出学生列表 Excel（教秘）：直接写入 response 输出流 */
    // Swagger 接口说明：导出学生列表 Excel
    @ApiOperation("导出学生列表 Excel")
    // GET 映射：访问 /api/accounts/students/export 触发导出
    @GetMapping("/students/export")
    // 导出学生列表：不返回 JSON，而是把 Excel 文件直接写入 response 输出流供浏览器下载
    public void exportStudents(HttpServletResponse response) {
        // 调用服务层生成学生 Excel 并写入响应流
        accountService.exportStudents(response);
    }

    /** 下载学生导入模板 Excel（教秘）：供批量导入学生使用 */
    // Swagger 接口说明：下载学生导入模板
    @ApiOperation("下载学生导入模板")
    // GET 映射：访问 /api/accounts/students/template 下载模板
    @GetMapping("/students/template")
    // 下载学生导入模板：同样直接写响应流，供批量导入前下载标准格式模板
    public void downloadStudentTemplate(HttpServletResponse response) {
        // 调用服务层输出学生导入模板 Excel
        accountService.downloadStudentTemplate(response);
    }

    /** 批量导入学生（教秘）：解析上传 Excel 创建账号，返回每人随机初始密码 */
    // Swagger 接口说明：批量导入学生（返回每人随机初始密码）
    @ApiOperation("批量导入学生（返回每人随机初始密码）")
    // POST 映射：访问 /api/accounts/students/import 上传 Excel 批量导入
    @PostMapping("/students/import")
    // 批量导入学生：接收 multipart 文件参数 file（"file" 为前端上传字段名）
    public Result<List<AccountImportResultVO>> importStudents(@RequestParam("file") MultipartFile file) {
        // 调用服务层解析 Excel 批量创建学生账号，返回每人随机初始密码的导入结果列表
        return Result.success(accountService.importStudents(file));
    }

    /** 导出教职工列表 Excel（教秘）：直接写入 response 输出流 */
    // Swagger 接口说明：导出教职工列表 Excel
    @ApiOperation("导出教职工列表 Excel")
    // GET 映射：访问 /api/accounts/staffs/export 触发导出
    @GetMapping("/staffs/export")
    // 导出教职工列表：直接写 Excel 到 response 输出流供下载
    public void exportStaffs(HttpServletResponse response) {
        // 调用服务层生成教职工 Excel 并写入响应流
        accountService.exportStaffs(response);
    }

    /** 下载教职工导入模板 Excel（教秘）：供批量导入教职工使用 */
    // Swagger 接口说明：下载教职工导入模板
    @ApiOperation("下载教职工导入模板")
    // GET 映射：访问 /api/accounts/staffs/template 下载模板
    @GetMapping("/staffs/template")
    // 下载教职工导入模板：直接写响应流，供批量导入前下载标准格式模板
    public void downloadStaffTemplate(HttpServletResponse response) {
        // 调用服务层输出教职工导入模板 Excel
        accountService.downloadStaffTemplate(response);
    }

    /** 批量导入教职工（教秘）：仅允许导入 TEACHER，返回每人随机初始密码 */
    // Swagger 接口说明：批量导入教职工（返回每人随机初始密码；仅允许导入 TEACHER）
    @ApiOperation("批量导入教职工（返回每人随机初始密码；仅允许导入 TEACHER）")
    // POST 映射：访问 /api/accounts/staffs/import 上传 Excel 批量导入
    @PostMapping("/staffs/import")
    // 批量导入教职工：接收 multipart 文件参数 file（"file" 为前端上传字段名）
    public Result<List<AccountImportResultVO>> importStaffs(@RequestParam("file") MultipartFile file) {
        // 调用服务层解析 Excel 批量创建教职工账号（仅允许 TEACHER），返回随机初始密码结果列表
        return Result.success(accountService.importStaffs(file));
    }
}
