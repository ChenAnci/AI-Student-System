package com.example.sms.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用名称-数值统计项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NameValue {

    private String name;
    private Long value;
}
