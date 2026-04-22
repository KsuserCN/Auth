package cn.ksuser.api.dto;

import cn.ksuser.api.entity.UserPasskey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Passkey 注册完成响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyRegistrationVerifyResponse {
    private Long passkeyId;
    private String passkeyName;
    private LocalDateTime createdAt;

    public static PasskeyRegistrationVerifyResponse fromUserPasskey(UserPasskey passkey) {
        return new PasskeyRegistrationVerifyResponse(
            passkey.getId(),
            passkey.getName(),
            passkey.getCreatedAt()
        );
    }
}
