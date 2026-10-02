// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.Notification;             // 站内通知实体类，对应数据库 notification 表
import org.apache.ibatis.annotations.Mapper;            // MyBatis 的 @Mapper 注解，标记本接口为 Mapper

/**
 * 站内通知主表 Mapper：对应数据库表 notification，负责通知内容（标题/正文/发送者）的增删改查
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<Notification> 后，自动获得对通知主表的增删改查、分页等通用方法，
// 通知的"标题 / 正文 / 发送者"等基础数据操作均由此完成
public interface NotificationMapper extends BaseMapper<Notification> {
}
