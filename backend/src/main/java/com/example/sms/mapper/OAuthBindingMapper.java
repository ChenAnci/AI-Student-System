package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.OAuthBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth 绑定关系 Mapper
 */
@Mapper
public interface OAuthBindingMapper extends BaseMapper<OAuthBinding> {
}
