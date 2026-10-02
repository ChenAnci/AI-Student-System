// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper;      // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.NotificationReceiver;          // 通知接收明细实体类，对应数据库 notification_receiver 表
import org.apache.ibatis.annotations.Insert;                // @Insert 注解：标注自定义插入 SQL
import org.apache.ibatis.annotations.Mapper;                // MyBatis 的 @Mapper 注解，标记本接口为 Mapper
import org.apache.ibatis.annotations.Param;                 // @Param 注解：为 SQL 中 #{} 占位符 / 集合遍历绑定参数名

import java.util.List; // 列表集合类型，批量插入时用于接收接收人列表

/**
 * 通知接收明细表 Mapper：对应数据库表 notification_receiver，负责接收明细的查询与批量写入
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<NotificationReceiver> 获得明细表通用 CRUD；下方为自定义批量插入方法
public interface NotificationReceiverMapper extends BaseMapper<NotificationReceiver> {

    /**
     * 批量插入接收明细（F-3）：一次 INSERT 多行 VALUES，替代逐条 insert 的 N+1 写放大。
     * 群发通知接收人数可达数百，逐条 insert 会产生大量单行 INSERT 往返；
     * 批量拼接为一条语句，显著减少 SQL 解析/网络/日志开销。
     */
    // @Insert：标注下方拼接字符串为要执行的插入 SQL；以 "<script>" 开头，内部可使用 MyBatis 动态标签
    @Insert("<script>"
            // —— SQL 片段1：插入目标表与列清单（通知ID、学生ID、是否已读、创建时间），VALUES 关键字开启多行值插入 ——
            + "INSERT INTO notification_receiver (notification_id, student_id, is_read, created_at) VALUES "
            // —— SQL 片段2：<foreach> 动态遍历 receivers 集合 ——
            // collection='receivers'：要遍历的实参名；item='r'：每次取出的元素变量名；
            // separator=','：每个值行之间用英文逗号分隔（即展开为多行 VALUES，实现一次插入多行）
            + "<foreach collection='receivers' item='r' separator=','>"
            // —— SQL 片段3：每一行值的占位模板，取遍历项 r 的字段；NOW() 由数据库生成当前时间，避免逐条取 Java 时间 ——
            + "(#{r.notificationId}, #{r.studentId}, #{r.isRead}, NOW())"
            // 收尾：</foreach> 结束遍历；</script> 结束动态 SQL 标记
            + "</foreach>"
            + "</script>")
    // 方法定义：入参 receivers 为待插入的接收明细列表；返回 int 表示本次实际插入成功的行数
    int batchInsert(@Param("receivers") List<NotificationReceiver> receivers);
}
