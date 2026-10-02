// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.StudentCourse;            // 学生选课实体类，对应数据库 student_course 表
import org.apache.ibatis.annotations.Mapper;            // MyBatis 的 @Mapper 注解，标记本接口为 Mapper

/**
 * 学生选课表 Mapper：对应数据库表 student_course，负责选课记录（含成绩、考试标记）的增删改查
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<StudentCourse> 后，自动获得对选课表的增删改查等通用方法，
// 选课记录中的成绩字段、考试标记等数据操作均由此完成
public interface StudentCourseMapper extends BaseMapper<StudentCourse> {
}
