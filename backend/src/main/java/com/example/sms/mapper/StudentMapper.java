// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.Student;                  // 学生实体类，对应数据库 student 表
import org.apache.ibatis.annotations.Mapper;            // MyBatis 的 @Mapper 注解，标记本接口为 Mapper

/**
 * 学生表 Mapper：对应数据库表 student，提供学生数据的增删改查、分页等基础操作
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<Student> 后，自动获得对学生表的增删改查、分页等通用方法，
// 具体 SQL 由 MyBatis-Plus 根据实体类上的表/字段注解自动生成，无需手写
public interface StudentMapper extends BaseMapper<Student> {
}
