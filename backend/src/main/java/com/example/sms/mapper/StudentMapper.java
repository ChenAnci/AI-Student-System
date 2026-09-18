package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生表 Mapper：对应数据库表 student，提供学生数据的增删改查、分页等基础操作
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
