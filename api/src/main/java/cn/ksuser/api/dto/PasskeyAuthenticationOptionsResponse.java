package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passkey 认证选项响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyAuthenticationOptionsResponse {
    private String challenge; // base64 编码的 challenge
    private String challengeId; // challenge 的唯一标识符（用于后续验证）
    private String timeout; // 超时时间（毫秒）
    private String rpId; // RP ID
    private String userVerification; // 用户验证级别
    // allowCredentials 可为空（表示发现凭证）
}
