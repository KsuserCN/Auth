package cn.ksuser.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.apple")
public class AppleAuthProperties {
    private List<String> clientIds = new ArrayList<>();
    private String teamId = "";
    private String keyId = "";
    private String privateKeyPem = "";
    private String credentialEncryptionKey = "";
    public String getCredentialEncryptionKey() { return credentialEncryptionKey; }
    public void setCredentialEncryptionKey(String value) { credentialEncryptionKey=value; }
    public List<String> getClientIds() { return clientIds; }
    public void setClientIds(List<String> value) { clientIds = value; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { teamId = value; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String value) { keyId = value; }
    public String getPrivateKeyPem() { return privateKeyPem; }
    public void setPrivateKeyPem(String value) { privateKeyPem = value; }
    public void requireConfigured(String clientId) {
        if (clientId == null || !clientIds.contains(clientId) || teamId.isBlank() || keyId.isBlank() || privateKeyPem.isBlank() || credentialEncryptionKey.isBlank()) {
            throw new IllegalStateException("Apple 登录尚未配置，请联系管理员");
        }
    }
}
