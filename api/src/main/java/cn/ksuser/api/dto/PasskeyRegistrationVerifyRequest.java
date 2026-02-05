package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Passkey 注册完成请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyRegistrationVerifyRequest {
    private String credentialRawId; // base64 编码的 credential ID
    private String clientDataJSON; // base64 编码的 ClientDataJSON
    private String attestationObject; // base64 编码的 attestationObject
    private String passkeyName; // Passkey 名称（可选，覆盖注册选项中的名称）
    private String transports; // 传输方式（逗号分隔：usb,nfc,ble,internal）
}
