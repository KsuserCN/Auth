package cn.ksuser.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户 Passkey 列表响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyListResponse {
    private List<PasskeyInfo> passkeys;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasskeyInfo {
        private Long id;
        private String name;
        private String transports;
        private String lastUsedAt;
        private String createdAt;
    }
}
