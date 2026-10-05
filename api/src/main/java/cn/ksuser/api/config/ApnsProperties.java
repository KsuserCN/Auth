package cn.ksuser.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.apns")
public class ApnsProperties {
    private boolean enabled = false;
    private String teamId = "";
    // Apple scopes current APNs signing keys to Sandbox or Production.
    private String sandboxKeyId = "";
    private String sandboxPrivateKeyPath = "";
    private String productionKeyId = "";
    private String productionPrivateKeyPath = "";
    // Retained for deployments using an older key that is valid in both environments.
    private String keyId = "";
    private String privateKeyPath = "";
    private String topic = "cn.ksuser.auth";
    private int abnormalRiskThreshold = 40;
}
