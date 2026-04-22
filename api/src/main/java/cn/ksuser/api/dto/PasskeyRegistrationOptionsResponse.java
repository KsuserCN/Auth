package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passkey 注册选项响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyRegistrationOptionsResponse {
    private String challenge; // base64 编码的 challenge
    private String rp; // RP (Relying Party) 信息 JSON
    private String user; // 用户信息 JSON
    private String pubKeyCredParams; // 公钥参数 JSON
    private String timeout; // 超时时间（毫秒）
    private String attestation; // attestation 级别
    private String authenticatorSelection; // 认证器选择条件 JSON
}
