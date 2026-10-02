// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.OAuthBinding;             // OAuth 绑定实体类，对应第三方登录绑定关系表
import org.apache.ibatis.annotations.Mapper;            // MyBatis 的 @Mapper 注解，标记本接口为 Mapper

/**
 * OAuth 绑定关系 Mapper
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<OAuthBinding> 后，自动获得对 OAuth 绑定关系表的增删改查等通用方法，
// 用于维护用户与第三方平台（如微信等）账号之间的绑定关系
public interface OAuthBindingMapper extends BaseMapper<OAuthBinding> {
}
