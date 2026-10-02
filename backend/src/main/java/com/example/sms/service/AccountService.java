package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入 MyBatis-Plus 查询构造器、业务异常、DTO、实体、Excel 行对象、Mapper、工具类、Spring 相关注解与 Java 集合/并发工具 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询条件构造器：类型安全地拼装查询条件
import com.example.sms.common.BusinessException; // 自定义业务异常类：抛出后由全局异常处理器统一转换响应
import com.example.sms.dto.AccountCreateDTO; // 添加账号请求 DTO
import com.example.sms.dto.AccountUpdateDTO; // 编辑账号请求 DTO
import com.example.sms.entity.Staff; // 教职工实体类
import com.example.sms.entity.Student; // 学生实体类
import com.example.sms.excel.StaffExcelRow; // 教职工 Excel 行对象（导入/导出的数据载体）
import com.example.sms.excel.StudentExcelRow; // 学生 Excel 行对象
import com.example.sms.mapper.StaffMapper; // 教职工表 Mapper 接口
import com.example.sms.mapper.StudentMapper; // 学生表 Mapper 接口
import com.example.sms.util.ExcelUtil; // Excel 读写工具类
import com.example.sms.util.UserContext; // 用户上下文工具：读取当前登录用户信息
import com.example.sms.vo.AccountImportResultVO; // 导入结果视图对象（含姓名/账号/初始密码）
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 密码加密器
import org.springframework.stereotype.Service; // Spring 服务层注解
import org.springframework.transaction.annotation.Transactional; // Spring 声明式事务注解
import org.springframework.web.multipart.MultipartFile; // Spring 的文件上传对象（接收 Excel 文件）
import javax.servlet.http.HttpServletResponse; // Servlet 响应对象（用于导出 Excel 时写入输出流）
import java.math.BigDecimal; // 高精度十进制数：用于学分等金额/数值运算，避免浮点误差
import java.security.SecureRandom; // 密码学安全的随机数生成器：用于生成随机初始密码
import java.util.ArrayList; // 动态数组集合
import java.util.Collections; // 集合工具类
import java.util.HashSet; // 哈希集合：用于导入时对学号/工号去重
import java.util.List; // 列表接口
import java.util.Map; // 键值映射接口
import java.util.Set; // 集合接口
import java.util.concurrent.ConcurrentHashMap; // 并发安全的哈希表：用于工号/学号生成缓存
import java.util.stream.Collectors; // Stream 收集器：把流转换为集合

/**
 * 账号管理服务（教学秘书）
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class AccountService { // 账号管理服务类：账号的增删改查、密码重置、冻结启用、Excel 批量导入导出

    @Autowired // Spring 自动注入 StaffMapper
    private StaffMapper staffMapper; // 教职工表 Mapper

    @Autowired // Spring 自动注入 StudentMapper
    private StudentMapper studentMapper; // 学生表 Mapper

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10); // BCrypt 密码编码器（强度 10），用于密码散列

    private static final BigDecimal DEFAULT_REQUIRED_CREDITS = new BigDecimal("160.00"); // 新学生默认毕业要求学分 160 分

    // ===== 导入初始密码：随机生成 8 位（去除易混淆字符），避免统一弱口令 =====
    private static final SecureRandom RANDOM = new SecureRandom(); // 密码学安全随机数生成器，保证生成的初始密码不可预测
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"; // 可用字符集：剔除易混淆的 0/O、1/I/l 等字符，便于人工抄录

    private String randomInitPassword() { // 生成 8 位随机初始密码
        StringBuilder sb = new StringBuilder(8); // 创建容量为 8 的字符串构建器，用于拼接密码字符
        for (int i = 0; i < 8; i++) { // 循环 8 次，逐位生成密码
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length()))); // 从字符集随机取一个字符追加到密码
        }
        return sb.toString(); // 返回生成的 8 位随机密码明文
    }

    /** 校验当前用户必须是教学秘书 */
    public void checkAdmin() { // 权限校验：仅教学秘书（ADMIN）可执行管理操作
        if (!"ADMIN".equals(UserContext.getRole())) { // 当前登录用户角色不是 ADMIN
            throw new BusinessException(403, "无权限，仅教学秘书可操作"); // 抛出 403 无权限异常
        }
    }

    /** 教职工列表 */
    public List<Staff> listStaffs(String keyword) { // 查询教职工列表，支持按姓名/工号模糊搜索
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<>(); // 创建教职工查询条件构造器
        wrapper.like(keyword != null && !keyword.isBlank(), Staff::getRealName, keyword) // 关键词非空时按姓名模糊匹配
                .or(keyword != null && !keyword.isBlank(), w -> w.like(Staff::getStaffNo, keyword)) // 或按工号模糊匹配（两个条件 OR 连接）
                .orderByAsc(Staff::getStaffNo); // 结果按工号升序排列，便于查看
        return staffMapper.selectList(wrapper); // 执行查询并返回教职工列表
    }

    /** 学生列表 */
    public List<Student> listStudents(String keyword) { // 查询学生列表，支持按姓名/学号/专业/班级模糊搜索
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>(); // 创建学生查询条件构造器
        wrapper.and(keyword != null && !keyword.isBlank(), w -> w // 关键词非空时整体包裹一组条件（保证括号分组正确）
                        .like(Student::getRealName, keyword) // 按姓名模糊匹配
                        .or().like(Student::getStudentNo, keyword) // 或按学号模糊匹配
                        .or().like(Student::getMajor, keyword) // 或按专业模糊匹配
                        .or().like(Student::getClassName, keyword)) // 或按班级模糊匹配
                .orderByAsc(Student::getStudentNo); // 结果按学号升序排列
        return studentMapper.selectList(wrapper); // 执行查询并返回学生列表
    }

    /**
     * 添加账号（教师生成 T 工号，学生生成 S 学号），初始密码 123456。
     * 事务：账号记录与自动生成的工号/学号同事务写入，失败整体回滚，避免半截数据。
     * 调用逻辑：AccountController.add → accountService.createAccount：教秘在账号管理页提交表单，内部自动生成 T 工号/S 学号并以 123456 密文落库，返回后前端刷新账号列表。
     * 为什么：@Transactional 保证账号记录与工号/学号生成同事务原子提交，失败整体回滚不留半截数据；初始密码统一 123456，由用户首次登录后自助修改。
     */
    @Transactional // 声明式事务：该方法内所有数据库操作在同一事务中，任一步失败整体回滚
    public void createAccount(AccountCreateDTO dto) { // 创建账号：根据角色类型分别生成教师或学生账号
        checkAdmin(); // 校验操作者是教学秘书
        // 教师账号：自动生成 T 工号（如 T1003）；学生账号：自动生成 S 学号（如 S20230004）。
        // 初始密码统一 123456（由用户首次登录后自行修改），新账号默认 ENABLED 可用
        if ("TEACHER".equals(dto.getRoleType())) { // 角色为教师
            Staff staff = new Staff(); // 创建教职工实体
            staff.setStaffNo(nextNo("T", false)); // 自动生成下一个 T 工号（如 T1003）
            staff.setRealName(dto.getRealName()); // 设置真实姓名
            staff.setRoleType("TEACHER"); // 设置角色为教师
            staff.setStatus("ENABLED"); // 新账号默认启用
            staff.setDepartment(dto.getDepartment()); // 设置所属院系
            staff.setPhone(dto.getPhone()); // 设置联系电话
            staff.setPasswordHash(encoder.encode("123456")); // 初始密码统一 123456，存 BCrypt 密文
            staffMapper.insert(staff); // 插入教职工表
        } else if ("STUDENT".equals(dto.getRoleType())) { // 角色为学生
            Student student = new Student(); // 创建学生实体
            student.setStudentNo(nextNo("S", true)); // 自动生成下一个 S 学号（如 S20230004）
            student.setRealName(dto.getRealName()); // 设置真实姓名
            student.setStatus("ENABLED"); // 新账号默认启用
            student.setGender(dto.getGender()); // 设置性别
            student.setDepartment(dto.getDepartment()); // 设置所属院系
            student.setMajor(dto.getMajor()); // 设置专业
            student.setClassName(dto.getClassName()); // 设置班级
            student.setEnrollmentYear(dto.getEnrollmentYear()); // 设置入学年份
            student.setPhone(dto.getPhone()); // 设置联系电话
            // 新学生预设毕业要求学分 160，已修学分与 GPA 从 0 起算，后续随成绩发布逐步累加
            student.setRequiredCredits(DEFAULT_REQUIRED_CREDITS); // 毕业要求学分默认 160
            student.setTotalEarnedCredits(BigDecimal.ZERO); // 已修学分初始为 0
            student.setGpa(BigDecimal.ZERO); // GPA 初始为 0
            student.setPasswordHash(encoder.encode("123456")); // 初始密码统一 123456，存 BCrypt 密文
            studentMapper.insert(student); // 插入学生表
        } else { // 角色类型既不是教师也不是学生
            throw new BusinessException("角色仅支持 TEACHER 或 STUDENT"); // 抛出参数错误提示
        }
    }

    /**
     * 重置密码为 123456（重置后旧 token 全部失效：tokenVersion +1）。
     * 调用逻辑：AccountController.resetPassword → accountService.resetPassword：教秘在账号管理页对指定账号点击重置，将密码重置为 123456 并 tokenVersion+1，前端刷新列表。
     * 为什么：重置即强制回收账号控制权——tokenVersion+1 让该账号已签发的所有旧 JWT 立即失效，账号本人必须用新密码重新登录。
     */
    public void resetPassword(String userType, Long id) { // 重置指定账号的密码为 123456 并吊销旧令牌
        checkAdmin(); // 校验操作者是教学秘书
        if ("STAFF".equals(userType)) { // 目标账号为教职工
            Staff staff = staffMapper.selectById(id); // 按主键查询教职工
            if (staff == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            staff.setPasswordHash(encoder.encode("123456")); // 密码重置为 123456 的密文
            staff.setTokenVersion(nextTokenVersion(staff.getTokenVersion())); // token 版本号 +1：使旧 JWT 全部失效
            staffMapper.updateById(staff); // 更新教职工记录
        } else if ("STUDENT".equals(userType)) { // 目标账号为学生
            Student student = studentMapper.selectById(id); // 按主键查询学生
            if (student == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            student.setPasswordHash(encoder.encode("123456")); // 密码重置为 123456 的密文
            student.setTokenVersion(nextTokenVersion(student.getTokenVersion())); // token 版本号 +1：使旧 JWT 全部失效
            studentMapper.updateById(student); // 更新学生记录
        } else { // 账号类型非法
            throw new BusinessException("非法账号类型"); // 抛出参数错误提示
        }
    }

    /** token 版本号递增（null 视为 0）：改密/重置/禁用后使旧 token 失效 */
    private Integer nextTokenVersion(Integer v) { // 计算下一个 token 版本号：null 按 0 计，再 +1
        return (v == null ? 0 : v) + 1; // 版本号递增，返回新版本号
    }

    /**
     * 自助修改本人密码：校验旧密码后更新为新密码密文，并递增 token 版本号吊销旧 token。
     * 学生/教师/管理员均可调用；admin 走教职工表（与登录路由一致）。
     * 事务：密码密文与 token 版本号须同库原子更新，任一步失败整体回滚，
     * 防止出现"密码已改但旧 token 未吊销"的中间态。
     * 调用逻辑：AccountController.changePassword → accountService.changePassword：登录用户在个人中心提交旧/新密码，校验通过后更新密文并 tokenVersion+1，前端提示重新登录。
     * 为什么：改密后 tokenVersion+1 使旧 JWT 签名全部失效（吊销旧令牌），防止改密前泄露的 token 继续可用；事务保证密文与版本号原子更新，杜绝"密码已改但旧 token 未吊销"的中间态。
     */
    @Transactional // 声明式事务：密文与版本号必须原子更新，避免中间态
    public void changePassword(String oldPassword, String newPassword) { // 修改当前登录用户自己的密码
        Long userId = UserContext.getUserId(); // 从上下文取当前登录用户 ID
        String role = UserContext.getRole(); // 从上下文取当前登录用户角色
        if (userId == null) { // 用户未登录（上下文无用户 ID）
            throw new BusinessException("未登录或登录已过期"); // 提示先登录
        }
        // 新密码强度校验（与 DTO 注解一致，服务层兜底防绕过前端/接口层校验）
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 20 // 密码为空或长度不在 8~20 位
                || !newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) { // 或不同时包含字母和数字（正则校验）
            throw new BusinessException("新密码需为 8~20 位且同时包含字母和数字"); // 提示密码强度不达标
        }
        if ("STUDENT".equals(role)) { // 当前用户是学生
            Student student = studentMapper.selectById(userId); // 按 ID 查询学生
            if (student == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            if (!encoder.matches(oldPassword, student.getPasswordHash())) { // 旧密码比对失败
                throw new BusinessException("旧密码错误"); // 提示旧密码错误
            }
            if (encoder.matches(newPassword, student.getPasswordHash())) { // 新密码与旧密码相同
                throw new BusinessException("新密码不能与旧密码相同"); // 禁止新旧密码相同
            }
            student.setPasswordHash(encoder.encode(newPassword)); // 存储新密码的 BCrypt 密文
            student.setTokenVersion(nextTokenVersion(student.getTokenVersion())); // token 版本号 +1 吊销旧令牌
            studentMapper.updateById(student); // 更新学生记录
        } else { // 当前用户是教师或管理员（admin 走教职工表，与登录路由一致）
            Staff staff = staffMapper.selectById(userId); // 按 ID 查询教职工
            if (staff == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            if (!encoder.matches(oldPassword, staff.getPasswordHash())) { // 旧密码比对失败
                throw new BusinessException("旧密码错误"); // 提示旧密码错误
            }
            if (encoder.matches(newPassword, staff.getPasswordHash())) { // 新密码与旧密码相同
                throw new BusinessException("新密码不能与旧密码相同"); // 禁止新旧密码相同
            }
            staff.setPasswordHash(encoder.encode(newPassword)); // 存储新密码的 BCrypt 密文
            staff.setTokenVersion(nextTokenVersion(staff.getTokenVersion())); // token 版本号 +1 吊销旧令牌
            staffMapper.updateById(staff); // 更新教职工记录
        }
    }

    /**
     * 编辑账号信息（学号/工号与角色不可修改）。
     * 调用逻辑：AccountController.update → accountService.updateAccount：教秘在账号编辑页提交姓名/联系方式/专业班级等资料，保存后前端刷新列表。
     * 为什么：学号/工号与角色是账号唯一标识，禁止修改以保护既有选课/成绩记录与登录身份的关联关系；编辑不涉及凭证变更，无需递增 tokenVersion。
     */
    public void updateAccount(String userType, Long id, AccountUpdateDTO dto) { // 编辑账号的基本资料信息
        checkAdmin(); // 校验操作者是教学秘书
        // 编辑仅允许改姓名/联系方式/专业班级等个人信息；学号/工号与角色作为账号唯一标识不可修改，
        // 否则会破坏已存在的选课记录、成绩记录与登录身份的关联关系
        if ("STAFF".equals(userType)) { // 目标账号为教职工
            Staff staff = staffMapper.selectById(id); // 按主键查询教职工
            if (staff == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            staff.setRealName(dto.getRealName()); // 更新姓名
            staff.setDepartment(dto.getDepartment()); // 更新院系
            staff.setPhone(dto.getPhone()); // 更新联系电话
            staffMapper.updateById(staff); // 落库更新
        } else if ("STUDENT".equals(userType)) { // 目标账号为学生
            Student student = studentMapper.selectById(id); // 按主键查询学生
            if (student == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            student.setRealName(dto.getRealName()); // 更新姓名
            student.setGender(dto.getGender()); // 更新性别
            student.setPhone(dto.getPhone()); // 更新联系电话
            student.setDepartment(dto.getDepartment()); // 更新院系
            student.setMajor(dto.getMajor()); // 更新专业
            student.setClassName(dto.getClassName()); // 更新班级
            student.setEnrollmentYear(dto.getEnrollmentYear()); // 更新入学年份
            studentMapper.updateById(student); // 落库更新
        } else { // 账号类型非法
            throw new BusinessException("非法账号类型"); // 抛出参数错误提示
        }
    }

    /**
     * 冻结/启用账号（状态变更后递增 token 版本号，吊销该账号所有旧 token）。
     * 调用逻辑：AccountController.updateStatus → accountService.toggleStatus：教秘在账号管理页冻结/启用账号，先校验状态枚举合法性再更新 status 并 tokenVersion+1，前端刷新列表。
     * 为什么：FROZEN/SUSPENDED 状态由登录/选课等流程检查拦截，同时 tokenVersion+1 吊销已签发旧 token，做到"冻结即下线"；先校验状态枚举防脏数据入库。
     */
    public void toggleStatus(String userType, Long id, String status) { // 冻结/启用账号状态
        checkAdmin(); // 校验操作者是教学秘书
        // 账号状态机：ENABLED(正常)/FROZEN(冻结，不可登录与选课)/SUSPENDED(休学，不可选课)；
        // 先校验状态枚举合法性，避免脏数据入库
        if (!"ENABLED".equals(status) && !"FROZEN".equals(status) && !"SUSPENDED".equals(status)) { // 状态不在合法枚举范围内
            throw new BusinessException("非法状态"); // 拒绝非法状态值，防止脏数据
        }
        if ("STAFF".equals(userType)) { // 目标账号为教职工
            Staff staff = staffMapper.selectById(id); // 按主键查询教职工
            if (staff == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            staff.setStatus(status); // 更新账号状态（启用/冻结/休学）
            staff.setTokenVersion(nextTokenVersion(staff.getTokenVersion())); // token 版本号 +1：冻结即下线，旧令牌全部失效
            staffMapper.updateById(staff); // 落库更新
        } else if ("STUDENT".equals(userType)) { // 目标账号为学生
            Student student = studentMapper.selectById(id); // 按主键查询学生
            if (student == null) throw new BusinessException("账号不存在"); // 账号不存在则抛异常
            student.setStatus(status); // 更新账号状态
            student.setTokenVersion(nextTokenVersion(student.getTokenVersion())); // token 版本号 +1：冻结即下线
            studentMapper.updateById(student); // 落库更新
        } else { // 账号类型非法
            throw new BusinessException("非法账号类型"); // 抛出参数错误提示
        }
    }

    /** 当前教师/学生查看本人信息 */
    public Object myInfo() { // 查询当前登录用户的个人信息（学生返回学生实体，其余返回教职工实体）
        Long userId = UserContext.getUserId(); // 从上下文取当前登录用户 ID
        String role = UserContext.getRole(); // 从上下文取当前登录用户角色
        if ("STUDENT".equals(role)) { // 当前用户是学生
            return studentMapper.selectById(userId); // 返回学生实体（含全部个人字段）
        }
        return staffMapper.selectById(userId); // 其余角色（教师/管理员）返回教职工实体
    }

    // ==================== Excel 导入导出 ====================

    /** 导出学生列表 */
    public void exportStudents(HttpServletResponse response) { // 把全部学生列表导出为 Excel 文件
        checkAdmin(); // 校验操作者是教学秘书
        // 导出全部学生（不含密码等敏感字段），供线下核对/归档
        List<Student> students = listStudents(null); // 不带关键词查询全部学生
        List<StudentExcelRow> rows = students.stream().map(s -> { // 将学生实体流式转换为 Excel 行对象
            StudentExcelRow r = new StudentExcelRow(); // 创建一行学生 Excel 数据
            r.setRealName(s.getRealName()); // 填充姓名
            r.setStudentNo(s.getStudentNo()); // 填充学号
            r.setGender(s.getGender()); // 填充性别
            r.setDepartment(s.getDepartment()); // 填充院系
            r.setMajor(s.getMajor()); // 填充专业
            r.setClassName(s.getClassName()); // 填充班级
            r.setEnrollmentYear(s.getEnrollmentYear()); // 填充入学年份
            r.setPhone(s.getPhone()); // 填充联系电话
            return r; // 返回组装好的行对象
        }).collect(Collectors.toList()); // 收集为行对象列表
        ExcelUtil.write(response, "学生列表", StudentExcelRow.class, rows); // 调用工具类把行列表写出为 Excel（文件名：学生列表）
    }

    /** 学生导入模板 */
    public void downloadStudentTemplate(HttpServletResponse response) { // 下载学生导入模板（仅表头，无数据行）
        checkAdmin(); // 校验操作者是教学秘书
        ExcelUtil.write(response, "学生导入模板", StudentExcelRow.class, Collections.emptyList()); // 写入空数据行的模板文件
    }

    /**
     * 批量导入学生：学号留空自动生成，初始密码为随机 8 位（随导入结果返回，供线下分发）。
     * 先整体校验，存在错误则整批不导入并返回错误明细。
     * 事务：全部行校验通过后统一插入，任一行失败整批回滚，杜绝"部分导入成功"的脏数据。
     */
    @Transactional // 声明式事务：全部校验通过后统一插入，任一行失败整批回滚
    public List<AccountImportResultVO> importStudents(MultipartFile file) { // 批量导入学生：返回每行的初始密码结果
        checkAdmin(); // 校验操作者是教学秘书
        List<ExcelUtil.RowItem<StudentExcelRow>> rows = ExcelUtil.readWithRowNumbers(file, StudentExcelRow.class); // 读取 Excel 全部数据行（带原始行号便于报错定位）
        if (rows.isEmpty()) { // 文件中没有任何有效数据行
            throw new BusinessException("未读取到任何有效数据，请使用模板填写后导入"); // 提示使用模板
        }
        List<String> errors = new ArrayList<>(); // 收集所有校验错误信息
        Set<String> noSet = new HashSet<>(); // 记录本次文件内出现过的学号，用于查重
        List<Student> toInsert = new ArrayList<>(); // 待插入的学生实体列表（全部校验通过后统一插入）
        List<AccountImportResultVO> result = new ArrayList<>(); // 导入结果列表（姓名、学号、初始密码）
        for (ExcelUtil.RowItem<StudentExcelRow> item : rows) { // 逐行遍历 Excel 数据
            StudentExcelRow row = item.getData(); // 取当前行的数据对象
            int rowNum = item.getRowNum(); // 取当前行在 Excel 中的行号（用于报错提示）
            String name = trimToNull(row.getRealName()); // 姓名去首尾空白（空白视为 null）
            if (name == null) { // 姓名为空
                errors.add("第" + rowNum + "行：姓名不能为空"); // 记录该行错误
                continue; // 跳过本行继续下一行
            }
            String no = trimToNull(row.getStudentNo()); // 学号去首尾空白（可为空，空则自动生成）
            if (no != null) { // 用户填了学号，需要做重复性校验
                // 文件内重复（noSet 去重）与库内已存在（selectCount）双重校验，学号留空则由系统自动生成
                if (!noSet.add(no)) { // 学号在本文件内已经出现过
                    errors.add("第" + rowNum + "行：学号 " + no + " 在文件中重复"); // 记录重复错误
                    continue; // 跳过本行
                }
                Long count = studentMapper.selectCount(new LambdaQueryWrapper<Student>() // 统计库中是否已存在该学号
                        .eq(Student::getStudentNo, no)); // 等值条件：student_no = 学号
                if (count > 0) { // 库中已存在该学号
                    errors.add("第" + rowNum + "行：学号 " + no + " 已存在"); // 记录已存在错误
                    continue; // 跳过本行
                }
            }
            Student student = new Student(); // 创建学生实体
            student.setStudentNo(no != null ? no : nextNo("S", true)); // 学号优先用文件中的，留空则自动生成下一个 S 学号
            student.setRealName(name); // 设置姓名
            student.setStatus("ENABLED"); // 新导入账号默认启用
            student.setGender(trimToNull(row.getGender())); // 设置性别（空白视为 null）
            student.setDepartment(trimToNull(row.getDepartment())); // 设置院系
            student.setMajor(trimToNull(row.getMajor())); // 设置专业
            student.setClassName(trimToNull(row.getClassName())); // 设置班级
            student.setEnrollmentYear(row.getEnrollmentYear() != null ? row.getEnrollmentYear() // 入学年份优先取文件值
                    : java.time.Year.now().getValue()); // 文件未填则默认取当前年份
            student.setPhone(trimToNull(row.getPhone())); // 设置联系电话
            student.setRequiredCredits(DEFAULT_REQUIRED_CREDITS); // 毕业要求学分默认 160
            student.setTotalEarnedCredits(BigDecimal.ZERO); // 已修学分初始 0
            student.setGpa(BigDecimal.ZERO); // GPA 初始 0
            // 随机初始密码（明文仅此一次随导入结果返回，供线下分发给学生，库中只存 BCrypt 密文）
            String initPwd = randomInitPassword(); // 生成 8 位随机初始密码
            student.setPasswordHash(encoder.encode(initPwd)); // 密码只存 BCrypt 密文
            result.add(new AccountImportResultVO(name, student.getStudentNo(), initPwd)); // 记录结果（明文仅本次返回，供线下分发）
            toInsert.add(student); // 加入待插入列表
        }
        if (!errors.isEmpty()) { // 存在任意校验错误
            throw new BusinessException("导入失败（共 " + errors.size() + " 处错误），请修正后重新导入：\n" // 汇总错误数量并提示
                    + String.join("\n", errors.subList(0, Math.min(errors.size(), 10)))); // 最多展示前 10 条错误明细，避免响应过长
        }
        for (Student student : toInsert) { // 全部校验通过，逐条插入
            studentMapper.insert(student); // 插入学生表
        }
        return result; // 返回导入结果（含初始密码，供线下分发）
    }

    /** 导出教职工列表 */
    public void exportStaffs(HttpServletResponse response) { // 把全部教职工列表导出为 Excel 文件
        checkAdmin(); // 校验操作者是教学秘书
        List<Staff> staffs = listStaffs(null); // 不带关键词查询全部教职工
        List<StaffExcelRow> rows = staffs.stream().map(s -> { // 将教职工实体流式转换为 Excel 行对象
            StaffExcelRow r = new StaffExcelRow(); // 创建一行教职工 Excel 数据
            r.setRealName(s.getRealName()); // 填充姓名
            r.setStaffNo(s.getStaffNo()); // 填充工号
            r.setRoleType(s.getRoleType()); // 填充角色类型
            r.setDepartment(s.getDepartment()); // 填充院系
            r.setPhone(s.getPhone()); // 填充联系电话
            return r; // 返回组装好的行对象
        }).collect(Collectors.toList()); // 收集为行对象列表
        ExcelUtil.write(response, "教职工列表", StaffExcelRow.class, rows); // 写出为 Excel 文件（文件名：教职工列表）
    }

    /** 教职工导入模板 */
    public void downloadStaffTemplate(HttpServletResponse response) { // 下载教职工导入模板（仅表头，无数据行）
        checkAdmin(); // 校验操作者是教学秘书
        ExcelUtil.write(response, "教职工导入模板", StaffExcelRow.class, Collections.emptyList()); // 写入空数据行的模板文件
    }

    /**
     * 批量导入教职工：工号留空自动生成，初始密码为随机 8 位（随导入结果返回，供线下分发）。
     * 安全限制：导入仅支持 TEACHER，不允许通过导入创建 ADMIN 账号（管理员必须在系统内人工创建）。
     * 事务：全部行校验通过后统一插入，任一行失败整批回滚，杜绝"部分导入成功"的脏数据。
     */
    @Transactional // 声明式事务：全部校验通过后统一插入，任一行失败整批回滚
    public List<AccountImportResultVO> importStaffs(MultipartFile file) { // 批量导入教职工：返回每行的初始密码结果
        checkAdmin(); // 校验操作者是教学秘书
        List<ExcelUtil.RowItem<StaffExcelRow>> rows = ExcelUtil.readWithRowNumbers(file, StaffExcelRow.class); // 读取 Excel 全部数据行（带原始行号）
        if (rows.isEmpty()) { // 文件中没有任何有效数据行
            throw new BusinessException("未读取到任何有效数据，请使用模板填写后导入"); // 提示使用模板
        }
        List<String> errors = new ArrayList<>(); // 收集所有校验错误信息
        Set<String> noSet = new HashSet<>(); // 记录本次文件内出现过的工号，用于查重
        List<Staff> toInsert = new ArrayList<>(); // 待插入的教职工实体列表
        List<AccountImportResultVO> result = new ArrayList<>(); // 导入结果列表（姓名、工号、初始密码）
        for (ExcelUtil.RowItem<StaffExcelRow> item : rows) { // 逐行遍历 Excel 数据
            StaffExcelRow row = item.getData(); // 取当前行的数据对象
            int rowNum = item.getRowNum(); // 取当前行在 Excel 中的行号
            String name = trimToNull(row.getRealName()); // 姓名去首尾空白（空白视为 null）
            if (name == null) { // 姓名为空
                errors.add("第" + rowNum + "行：姓名不能为空"); // 记录该行错误
                continue; // 跳过本行
            }
            String role = trimToNull(row.getRoleType()); // 角色去首尾空白（可为空，空默认教师）
            // 安全限制：导入只允许创建教师账号，ADMIN 必须人工创建，防止通过 Excel 批量提权
            if (role != null && !"TEACHER".equals(role)) { // 填了角色但不是 TEACHER（如试图导入 ADMIN）
                errors.add("第" + rowNum + "行：角色仅支持 TEACHER（管理员账号不允许通过导入创建，请在系统中人工创建）"); // 记录安全限制错误
                continue; // 跳过本行
            }
            String no = trimToNull(row.getStaffNo()); // 工号去首尾空白（可为空，空则自动生成）
            if (no != null) { // 用户填了工号，需要做重复性校验
                if (!noSet.add(no)) { // 工号在本文件内已经出现过
                    errors.add("第" + rowNum + "行：工号 " + no + " 在文件中重复"); // 记录重复错误
                    continue; // 跳过本行
                }
                Long count = staffMapper.selectCount(new LambdaQueryWrapper<Staff>() // 统计库中是否已存在该工号
                        .eq(Staff::getStaffNo, no)); // 等值条件：staff_no = 工号
                if (count > 0) { // 库中已存在该工号
                    errors.add("第" + rowNum + "行：工号 " + no + " 已存在"); // 记录已存在错误
                    continue; // 跳过本行
                }
            }
            Staff staff = new Staff(); // 创建教职工实体
            staff.setStaffNo(no != null ? no : nextNo("T", false)); // 工号优先用文件中的，留空则自动生成下一个 T 工号
            staff.setRealName(name); // 设置姓名
            staff.setRoleType(role != null ? role : "TEACHER"); // 角色未填时默认教师
            staff.setStatus("ENABLED"); // 新导入账号默认启用
            staff.setDepartment(trimToNull(row.getDepartment())); // 设置院系
            staff.setPhone(trimToNull(row.getPhone())); // 设置联系电话
            String initPwd = randomInitPassword(); // 生成 8 位随机初始密码
            staff.setPasswordHash(encoder.encode(initPwd)); // 密码只存 BCrypt 密文
            result.add(new AccountImportResultVO(name, staff.getStaffNo(), initPwd)); // 记录结果（明文仅本次返回，供线下分发）
            toInsert.add(staff); // 加入待插入列表
        }
        if (!errors.isEmpty()) { // 存在任意校验错误
            throw new BusinessException("导入失败（共 " + errors.size() + " 处错误），请修正后重新导入：\n" // 汇总错误数量并提示
                    + String.join("\n", errors.subList(0, Math.min(errors.size(), 10)))); // 最多展示前 10 条错误明细
        }
        for (Staff staff : toInsert) { // 全部校验通过，逐条插入
            staffMapper.insert(staff); // 插入教职工表
        }
        return result; // 返回导入结果（含初始密码，供线下分发）
    }

    private static String trimToNull(String value) { // 字符串去空白工具：空白字符串统一转为 null
        if (value == null) return null; // 原值为 null 直接返回
        String trimmed = value.trim(); // 去掉首尾空白
        return trimmed.isEmpty() ? null : trimmed; // 去空白后为空则视为 null，否则返回修剪后的字符串
    }

    /**
     * 生成下一个工号/学号（T1002 -> T1003，S20230003 -> S20230004）。
     * 带进程内缓存 + 同步：解决同批次（事务内多次查询最大号不变）与并发下生成重复号的问题，
     * 缓存以 DB 最大号与本次已生成号的较大者为基准递增。
     */
    private final Map<String, Long> nextNoCache = new ConcurrentHashMap<>(); // 已生成号码缓存：key 为前缀（T/S），value 为已用到的最大数值，防止同批/并发重复

    private String nextNo(String prefix, boolean student) { // 生成下一个工号/学号：prefix 为前缀（T 或 S），student 标识查询学生表还是教职工表
        synchronized (nextNoCache) { // 同步锁：保证同一时刻只有一个线程推进号码生成，避免并发重复
            String maxNo; // 存放库中当前最大号码
            // 按数字后缀的数值大小取最大号（字符串字典序会把 S2023009 排在 S20230010 之后，导致跨位撞号）
            if (student) { // 学生学号：查询学生表最大学号
                maxNo = studentMapper.selectList(new LambdaQueryWrapper<Student>() // 按学号数字部分降序取第一条
                                .last("ORDER BY CAST(SUBSTRING(student_no, 2) AS UNSIGNED) DESC LIMIT 1")) // 使用 CAST 把学号数字部分转数值排序，避免字典序跨位问题
                        .stream().map(Student::getStudentNo).findFirst().orElse(null); // 取第一条的学号，无记录则为 null
            } else { // 教职工工号：查询教职工表最大工号
                maxNo = staffMapper.selectList(new LambdaQueryWrapper<Staff>() // 按工号数字部分降序取第一条
                                .last("ORDER BY CAST(SUBSTRING(staff_no, 2) AS UNSIGNED) DESC LIMIT 1")) // 同上：按数值排序取最大号
                        .stream().map(Staff::getStaffNo).findFirst().orElse(null); // 取第一条的工号，无记录则为 null
            }
            long base = 0; // 基准值：默认从 0 开始（库中无记录时）
            if (maxNo != null) { // 库中存在历史最大号
                String numPart = maxNo.replaceAll("[^0-9]", ""); // 提取号码中的纯数字部分（去掉前缀字母）
                base = numPart.isEmpty() ? 0 : Long.parseLong(numPart); // 数字部分解析为 long 作为基准值
            }
            long cached = nextNoCache.getOrDefault(prefix, 0L); // 读取本次进程内已生成到的号码（无记录为 0）
            long next = Math.max(base, cached) + 1; // 取"库中最大号"与"本次已生成号"的较大者 +1，避免同批次重复
            nextNoCache.put(prefix, next); // 更新进程内缓存，供同批次后续调用递增
            return prefix + next; // 拼装并返回完整号码（如 T1003）
        }
    }
}
