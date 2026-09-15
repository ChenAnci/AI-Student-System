package com.example.sms.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号导入结果（含随机初始密码，供导入者线下分发）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountImportResultVO {
    private String realName;
    private String userNo;
    private String initPassword;
}
