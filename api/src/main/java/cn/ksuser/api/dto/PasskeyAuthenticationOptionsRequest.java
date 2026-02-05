package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passkey 认证选项请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyAuthenticationOptionsRequest {
    // 可选：邮箱或用户名（用于允许发现现有凭证）
    private String email;
    private String username;
}
