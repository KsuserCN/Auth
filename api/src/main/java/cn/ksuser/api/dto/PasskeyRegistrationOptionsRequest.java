package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passkey 注册选项请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyRegistrationOptionsRequest {
    private String passkeyName; // Passkey 名称/标签
    private String authenticatorType; // 认证器类型: auto | platform | cross-platform
}
