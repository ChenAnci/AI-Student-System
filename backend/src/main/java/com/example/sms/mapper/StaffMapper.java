package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.Staff;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教职工表 Mapper：对应数据库表 staff，提供教职工（管理员/教师/教秘）数据的增删改查等基础操作
 */
@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
}
