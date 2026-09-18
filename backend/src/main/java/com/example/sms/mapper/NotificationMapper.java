package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知主表 Mapper：对应数据库表 notification，负责通知内容（标题/正文/发送者）的增删改查
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
