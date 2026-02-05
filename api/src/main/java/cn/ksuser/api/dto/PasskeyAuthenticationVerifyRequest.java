package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passkey 认证验证请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyAuthenticationVerifyRequest {
    private String credentialRawId; // base64 编码的 credential ID
    private String clientDataJSON; // base64 编码的 ClientDataJSON
    private String authenticatorData; // base64 编码的 authenticatorData
    private String signature; // base64 编码的签名
}
