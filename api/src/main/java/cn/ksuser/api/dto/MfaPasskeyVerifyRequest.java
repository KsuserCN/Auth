package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA 登录时用于提交 Passkey 的请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaPasskeyVerifyRequest {
    private String mfaChallengeId;
    private String passkeyChallengeId;
    private String credentialRawId; // base64 编码的 credential ID
    private String clientDataJSON; // base64 编码的 ClientDataJSON
    private String authenticatorData; // base64 编码的 authenticatorData
    private String signature; // base64 编码的签名
}
