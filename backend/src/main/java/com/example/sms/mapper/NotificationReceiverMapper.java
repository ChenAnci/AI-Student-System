package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.NotificationReceiver;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationReceiverMapper extends BaseMapper<NotificationReceiver> {

    /**
     * 批量插入接收明细（F-3）：一次 INSERT 多行 VALUES，替代逐条 insert 的 N+1 写放大。
     * 群发通知接收人数可达数百，逐条 insert 会产生大量单行 INSERT 往返；
     * 批量拼接为一条语句，显著减少 SQL 解析/网络/日志开销。
     */
    @Insert("<script>"
            + "INSERT INTO notification_receiver (notification_id, student_id, is_read, created_at) VALUES "
            + "<foreach collection='receivers' item='r' separator=','>"
            + "(#{r.notificationId}, #{r.studentId}, #{r.isRead}, NOW())"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("receivers") List<NotificationReceiver> receivers);
}
